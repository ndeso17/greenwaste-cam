package id.pemilah.inference

import id.pemilah.model.InputSpec
import id.pemilah.model.ModelProfile
import id.pemilah.model.TensorLayout
import kotlin.math.exp

/**
 * Satu frame kamera dalam bentuk piksel ARGB (bukan tipe Android), supaya
 * praproses dan pengujian tidak bergantung pada `Bitmap`.
 */
class ArgbFrame(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val capturedAtMillis: Long = System.currentTimeMillis(),
) {
    init {
        require(pixels.size == width * height) {
            "ukuran piksel ${pixels.size} tidak sama dengan ${width}x${height}"
        }
    }
}

/**
 * Hasil satu kali klasifikasi: probabilitas per label model, urut sesuai label
 * yang tertulis di dalam model.
 */
data class Classification(
    val labels: List<String>,
    val probabilities: FloatArray,
) {
    /** Indeks label dengan probabilitas tertinggi; `-1` bila tidak ada label. */
    val topIndex: Int
        get() = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

    val topLabel: String? get() = labels.getOrNull(topIndex)

    val topProbability: Float get() = probabilities.getOrNull(topIndex) ?: 0f

    /**
     * `limit` kelas teratas, urut menurun. Dipakai panel uji klasifikasi supaya
     * operator melihat pesaing terdekat, bukan hanya pemenangnya.
     */
    fun ranked(limit: Int): List<Pair<String, Float>> = labels.indices
        .sortedByDescending { probabilities[it] }
        .take(limit)
        .map { labels[it] to probabilities[it] }

    // FloatArray membuat equals/hashCode bawaan data class menyesatkan; test
    // membandingkan isi secara eksplisit.
    override fun equals(other: Any?): Boolean =
        other is Classification &&
            labels == other.labels &&
            probabilities.contentEquals(other.probabilities)

    override fun hashCode(): Int = 31 * labels.hashCode() + probabilities.contentHashCode()

    companion object {
        /**
         * Softmax atas logits keluaran model. Nilai dikurangi maksimum lebih dulu
         * agar tidak meluap pada logits besar.
         */
        fun fromLogits(labels: List<String>, logits: FloatArray): Classification {
            if (logits.isEmpty()) return Classification(labels, FloatArray(0))
            val max = logits.max()
            val exponents = FloatArray(logits.size) { exp((logits[it] - max).toDouble()).toFloat() }
            val sum = exponents.sum().takeIf { it > 0f } ?: 1f
            return Classification(labels, FloatArray(exponents.size) { exponents[it] / sum })
        }
    }
}

/**
 * Sebab inferensi tidak menghasilkan klasifikasi. Kode stabil untuk teks UI
 * (D-03); tidak ada `OrtException` yang lolos ke layar.
 */
data class ClassifierFailure(val code: ClassifierFailureCode, val detail: String? = null)

enum class ClassifierFailureCode {
    /** Profil tidak menunjuk berkas model yang ada. */
    MODEL_FILE_MISSING,

    /** Berkas ada tetapi tidak dapat dibuka sebagai model ONNX. */
    MODEL_UNREADABLE,

    /** Bentuk keluaran tidak sesuai jumlah label profil. */
    OUTPUT_SHAPE_MISMATCH,

    /** Inferensi gagal saat dijalankan. */
    RUN_FAILED,
}

sealed interface ClassifierOutcome {
    data class Classified(val result: Classification) : ClassifierOutcome
    data class Failed(val failure: ClassifierFailure) : ClassifierOutcome
}

/** Kontrak klasifikasi frame; implementasi nyata memakai ONNX Runtime. */
interface Classifier {
    fun classify(profile: ModelProfile, frame: ArgbFrame): ClassifierOutcome

    /**
     * Klasifikasi beberapa frame sekaligus (petak overlay, D-14).
     *
     * Implementasi bawaan menjalankan satu per satu; implementasi ONNX Runtime
     * menumpuk petak menjadi satu batch agar hanya satu kali eksekusi sesi.
     */
    fun classifyAll(profile: ModelProfile, frames: List<ArgbFrame>): List<ClassifierOutcome> =
        frames.map { classify(profile, it) }
}

/**
 * Normalisasi masukan model klasifikasi (D-12).
 *
 * Model ResNet18 hasil pelatihan PyTorch lazim memakai normalisasi ImageNet,
 * sedangkan berkas model pengguna tidak memuat metadata `mean`/`std`. Nilai
 * ImageNet dipakai sebagai satu-satunya sumber angka normalisasi supaya mudah
 * diganti di satu tempat bila hasil di perangkat menunjukkan sebaliknya; asumsi
 * ini dicatat di `.work/FINDINGS.md`.
 */
object ImageNetNormalization {
    val mean: FloatArray = floatArrayOf(0.485f, 0.456f, 0.406f)
    val std: FloatArray = floatArrayOf(0.229f, 0.224f, 0.225f)
}

/**
 * Praproses frame menjadi tensor masukan model (murni, tanpa Android).
 *
 * Langkah: potong tengah menjadi persegi (sisi = sisi terpendek), ambil sampel
 * piksel terdekat ke ukuran masukan model, ubah 0..255 menjadi 0..1, lalu
 * normalisasi `(nilai - mean) / std` per kanal. Urutan kanal mengikuti
 * [TensorLayout] profil: NCHW (kanal lebih dulu) atau NHWC.
 */
object FramePreprocess {

    fun toTensor(
        frame: ArgbFrame,
        input: InputSpec,
        mean: FloatArray = ImageNetNormalization.mean,
        std: FloatArray = ImageNetNormalization.std,
    ): FloatArray {
        require(input.width > 0 && input.height > 0) {
            "ukuran masukan tidak sah: ${input.width}x${input.height}"
        }
        require(mean.size == 3 && std.size == 3) { "normalisasi harus tiga kanal" }
        val side = minOf(frame.width, frame.height)
        val offsetX = (frame.width - side) / 2
        val offsetY = (frame.height - side) / 2
        val output = FloatArray(3 * input.width * input.height)
        for (y in 0 until input.height) {
            val sourceY = offsetY + (y * side) / input.height
            for (x in 0 until input.width) {
                val sourceX = offsetX + (x * side) / input.width
                val argb = frame.pixels[sourceY * frame.width + sourceX]
                val channels = floatArrayOf(
                    ((argb shr 16) and 0xFF) / 255f,
                    ((argb shr 8) and 0xFF) / 255f,
                    (argb and 0xFF) / 255f,
                )
                for (channel in 0..2) {
                    val value = (channels[channel] - mean[channel]) / std[channel]
                    val index = when (input.layout) {
                        TensorLayout.NCHW -> channel * input.width * input.height +
                            y * input.width + x

                        TensorLayout.NHWC -> (y * input.width + x) * 3 + channel
                    }
                    output[index] = value
                }
            }
        }
        return output
    }
}
