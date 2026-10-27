package id.pemilah.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Aturan murni pembacaan metadata model ONNX (D-09, MOD-01, MOD-02).
 *
 * Kelas ini sengaja tidak menyentuh `ai.onnxruntime` maupun `android.*` supaya
 * seluruh aturan dapat diuji unit di JVM. Pemanggil (inspektor ORT) hanya
 * menyuplai nilai mentah: teks metadata `names` dan bentuk tensor.
 *
 * Aturan yang dijaga:
 * - Label dibaca berurutan sesuai indeks kelas model. Urutan adalah bagian dari
 *   kontrak model, jadi indeks yang hilang atau ganda ditolak, bukan dirapatkan
 *   diam-diam.
 * - Ukuran masukan diambil dari bentuk tensor; dimensi dinamis (bentuk `-1`)
 *   pada lebar/tinggi ditolak karena tidak memberi ukuran yang pasti.
 * - Keluarga keluaran diturunkan dari bentuk keluaran dan jumlah label; bentuk
 *   yang tidak dikenali ditolak dengan alasan, bukan ditebak (B-02 tetap terbuka).
 */
object OnnxMetadata {

    /** Kedalaman kanal yang didukung; nilai lain bukan keluaran YOLO yang dikenal. */
    private const val YOLO_V8_COORDINATES = 4
    private const val YOLO_V5_COORDINATES = 5

    private val json = Json { isLenient = true }

    /**
     * Baca metadata `names` menjadi label berurutan.
     *
     * Bentuk yang diterima mengikuti cara ultralytics menulis metadata:
     * objek `{"0": "Organik", "1": "Plastik"}`, bentuk `repr` Python
     * `{0: 'Organik', 1: 'Plastik'}`, atau larik `["Organik", "Plastik"]`.
     * Format lain (termasuk teks bebas) menghasilkan `null`.
     */
    fun parseClassNames(raw: String?): List<String>? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        // Ultralytics menulis `names` memakai kutip tunggal ala `repr` Python
        // (`{0: 'Organik'}`), sehingga teks dinormalkan lebih dulu bila bentuk
        // aslinya bukan JSON yang sah.
        val source = normalizedJson(text)
        val element = runCatching { json.parseToJsonElement(source) }.getOrNull() ?: return null
        val indexed: List<Pair<Int, String?>> = when (element) {
            is JsonArray -> element.mapIndexed { index, item -> index to item.textOrNull() }
            is JsonObject -> {
                // Parser JSON menyimpan kunci terakhir saja saat kunci ganda
                // muncul, sehingga kunci ganda harus dideteksi dari teks asli.
                if (hasDuplicateKeys(source)) return null
                element.entries.map { (key, value) ->
                    (key.toIntOrNull() ?: return null) to value.textOrNull()
                }
            }

            else -> return null
        }
        if (indexed.isEmpty()) return null
        if (indexed.any { (_, name) -> name == null }) return null
        val byIndex = indexed.associate { (index, name) -> index to requireNotNull(name) }
        // Indeks wajib lengkap 0..n-1 dan tidak ada yang ganda, karena urutan
        // indeks adalah urutan kanal keluaran model.
        if (byIndex.size != indexed.size) return null
        if (byIndex.keys.sorted() != byIndex.keys.sorted().indices.toList()) return null
        val names = byIndex.keys.sorted().map { requireNotNull(byIndex[it]) }
        if (names.any { it.isBlank() }) return null
        return names
    }

    /**
     * Turunkan spesifikasi masukan dari bentuk tensor, misalnya `[1, 3, 640, 640]`.
     *
     * `null` bila bentuk bukan empat dimensi, kanal tidak bernilai 3, atau
     * dimensi lebar/tinggi tidak pasti.
     */
    fun inputSpecOf(shape: List<Long>?): InputSpec? {
        val dims = shape ?: return null
        if (dims.size != 4) return null
        val (width, height, layout) = when {
            dims[1] == CHANNELS && dims[2] > 0 && dims[3] > 0 ->
                Triple(dims[3], dims[2], TensorLayout.NCHW)

            dims[3] == CHANNELS && dims[1] > 0 && dims[2] > 0 ->
                Triple(dims[2], dims[1], TensorLayout.NHWC)

            else -> return null
        }
        if (width > Int.MAX_VALUE || height > Int.MAX_VALUE) return null
        return InputSpec(
            width = width.toInt(),
            height = height.toInt(),
            // Normalisasi kanal gambar standar YOLO: piksel 0..255 dipetakan ke 0..1.
            normalization = Normalization.SCALE_0_1,
            layout = layout,
        )
    }

    /**
     * Turunkan keluarga keluaran dari bentuk keluaran dan jumlah label.
     *
     * Bentuk yang dikenali:
     * - `[1, 4 + C, N]` → YOLO v8 tanpa objekness;
     * - `[1, N, 5 + C]` → YOLO v5 dengan objekness;
     * - `[N, C]` → klasifikasi: satu label per gambar, tanpa kotak objek (D-10).
     *
     * `null` bila tidak ada keluaran yang cocok dengan jumlah label model.
     */
    fun outputSpecOf(shape: List<Long>?, classCount: Int): OutputSpec? {
        val dims = shape ?: return null
        if (classCount <= 0) return null
        if (dims.size == 2) {
            // Klasifikasi: sumbu terakhir adalah jumlah kelas. Sumbu batch boleh
            // dinamis (`-1`) karena tidak memengaruhi arti kanal.
            if (dims[1] != classCount.toLong()) return null
            return OutputSpec(
                format = OutputFormat.CLASSIFICATION_ONLY,
                boxesNormalized = false,
                hasObjectness = false,
            )
        }
        if (dims.size != 3) return null
        val channelsFirst = dims[1] == (YOLO_V8_COORDINATES + classCount).toLong()
        val channelsLast = dims[2] == (YOLO_V5_COORDINATES + classCount).toLong()
        return when {
            // Keluaran mentah YOLO memakai koordinat piksel masukan, bukan 0..1.
            channelsFirst -> OutputSpec(
                format = OutputFormat.YOLO_V8,
                boxesNormalized = false,
                hasObjectness = false,
            )

            channelsLast -> OutputSpec(
                format = OutputFormat.YOLO_V5,
                boxesNormalized = false,
                hasObjectness = true,
            )

            else -> null
        }
    }

    private fun kotlinx.serialization.json.JsonElement.textOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** Kunci objek JSON yang muncul lebih dari sekali; menandakan metadata rusak. */
    private fun hasDuplicateKeys(text: String): Boolean {
        val seen = mutableSetOf<String>()
        return KEY_PATTERN.findAll(text).any { match ->
            // Kunci boleh berupa string JSON atau angka tanpa kutip (bentuk repr).
            val key = match.groupValues.drop(1).first { it.isNotEmpty() }
            !seen.add(key)
        }
    }

    private val KEY_PATTERN = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:|(\\d+)\\s*:")

    /**
     * Ubah kutip tunggal ala `repr` Python menjadi JSON sah, tanpa menyentuh
     * tanda kutip yang sudah benar. Teks yang sudah berupa JSON dikembalikan apa
     * adanya supaya kunci ganda tetap terdeteksi.
     */
    private fun normalizedJson(text: String): String {
        if (text.none { it == '\'' }) return text
        val builder = StringBuilder(text.length)
        var inDouble = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            when {
                char == '\\' && index + 1 < text.length -> {
                    builder.append(text, index, index + 2)
                    index += 2
                    continue
                }

                char == '"' -> {
                    inDouble = !inDouble
                    builder.append(char)
                }

                // Kutip tunggal di luar string JSON adalah pembuka/penutup label.
                char == '\'' && !inDouble -> builder.append('"')

                else -> builder.append(char)
            }
            index++
        }
        return builder.toString()
    }

    private const val CHANNELS = 3L
}
