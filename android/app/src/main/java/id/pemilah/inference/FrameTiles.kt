package id.pemilah.inference

/**
 * Wilayah ternormalisasi 0..1 terhadap frame kamera (bukan koordinat kalibrasi).
 *
 * Dipakai kotak petak klasifikasi; koordinat mesin tetap milik kalibrasi dan
 * tidak pernah berasal dari sini (B-03).
 */
data class NormalizedRegion(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Satu petak frame beserta wilayahnya di dalam frame. */
data class FrameTile(val frame: ArgbFrame, val region: NormalizedRegion)

/**
 * Pemotongan frame menjadi petak untuk klasifikasi bergilir (D-14).
 *
 * Model klasifikasi tidak menghasilkan kotak objek, jadi posisi objek didekati
 * dengan mengklasifikasi beberapa petak dan menggambar kotak hanya pada petak
 * yang lolos ambang keyakinan. Kotak hasil cara ini **kasar**: batasnya adalah
 * batas petak, bukan tepi objek. Deteksi presisi menunggu model deteksi resmi
 * (B-17), dan batas ini dicatat di `.work/FINDINGS.md`.
 *
 * Pemotongan bersifat murni tanpa Android supaya dapat diuji; biaya inferensi
 * tidak diuji di sini.
 */
object FrameTiles {

    /**
     * Bagi [frame] menjadi `columns`×`rows` petak. Petak terakhir menyerap sisa
     * pembagian supaya seluruh frame tertutup tanpa celah.
     */
    fun split(frame: ArgbFrame, columns: Int, rows: Int): List<FrameTile> {
        require(columns > 0 && rows > 0) { "jumlah petak harus positif" }
        if (frame.width < columns || frame.height < rows) return emptyList()
        val tiles = ArrayList<FrameTile>(columns * rows)
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val left = column * frame.width / columns
                val right = if (column == columns - 1) {
                    frame.width
                } else {
                    (column + 1) * frame.width / columns
                }
                val top = row * frame.height / rows
                val bottom = if (row == rows - 1) {
                    frame.height
                } else {
                    (row + 1) * frame.height / rows
                }
                tiles += FrameTile(
                    frame = crop(frame, left, top, right, bottom),
                    region = NormalizedRegion(
                        left = left.toFloat() / frame.width,
                        top = top.toFloat() / frame.height,
                        right = right.toFloat() / frame.width,
                        bottom = bottom.toFloat() / frame.height,
                    ),
                )
            }
        }
        return tiles
    }

    private fun crop(frame: ArgbFrame, left: Int, top: Int, right: Int, bottom: Int): ArgbFrame {
        val width = right - left
        val height = bottom - top
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val sourceRow = (top + y) * frame.width + left
            System.arraycopy(frame.pixels, sourceRow, pixels, y * width, width)
        }
        return ArgbFrame(pixels, width, height)
    }
}

/** Satu petak yang lolos ambang keyakinan, siap digambar sebagai kotak. */
data class TileDetection(
    val region: NormalizedRegion,
    val label: String,
    val probability: Float,
    val referenceSimilarity: Float? = null,
)

/**
 * Penyaring petak menjadi kotak overlay (D-14).
 *
 * Aturan yang dijaga:
 * - Ambang `0.0` berarti "belum ditetapkan" (D-09); tanpa ambang tidak ada satu
 *   pun petak yang dianggap layak, sehingga tidak ada kotak yang dikarang.
 * - Petak diurutkan menurut keyakinan menurun dan dipotong pada [limit] supaya
 *   overlay tidak dipenuhi kotak saling tumpang tindih.
 * - Petak yang inferensinya gagal cukup dilewati; kegagalan dilaporkan panel
 *   hasil klasifikasi, bukan disamarkan sebagai kotak kosong.
 */
object TileDetectionFilter {

    fun select(
        tiles: List<FrameTile>,
        outcomes: List<ClassifierOutcome>,
        threshold: Double,
        limit: Int = 6,
    ): List<TileDetection> {
        if (threshold <= 0.0 || limit <= 0) return emptyList()
        return tiles.indices
            .mapNotNull { index ->
                val classification = (outcomes.getOrNull(index) as? ClassifierOutcome.Classified)
                    ?.result ?: return@mapNotNull null
                val label = classification.topLabel ?: return@mapNotNull null
                val probability = classification.topProbability
                if (probability < threshold) return@mapNotNull null
                TileDetection(
                    region = tiles[index].region,
                    label = label,
                    probability = probability,
                )
            }
            .sortedByDescending { it.probability }
            .take(limit)
    }
}