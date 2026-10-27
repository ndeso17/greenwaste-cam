package id.pemilah.model

/**
 * Hasil inspeksi berkas model ONNX (D-09, MOD-01, MOD-02).
 *
 * Inspeksi membaca identitas yang **sudah ada di dalam berkas model**: label
 * kelas dari metadata `names`, ukuran masukan dari bentuk tensor masukan, dan
 * keluarga keluaran dari bentuk tensor keluaran. Tidak ada nilai yang dikarang
 * aplikasi; bila model tidak memuat informasi tersebut, hasilnya adalah penolakan
 * beralasan, bukan profil dengan nilai bawaan (`Rules.md` §2.2).
 */
data class OnnxModelInfo(
    val labels: List<String>,
    val input: InputSpec,
    val output: OutputSpec,
    val modelVersion: String?,
) {
    /** Jumlah kelas yang benar-benar dikenali model. */
    val classCount: Int get() = labels.size
}

sealed interface OnnxInspectionResult {
    data class Inspected(val info: OnnxModelInfo) : OnnxInspectionResult

    data class Rejected(val problem: OnnxInspectionProblem) : OnnxInspectionResult
}

/**
 * Pembaca metadata dari berkas model ONNX.
 *
 * Abstraksi ini memisahkan ONNX Runtime (lapisan platform) dari alur impor
 * (lapisan model) supaya alur impor dapat diuji tanpa pustaka native.
 */
interface OnnxModelInspector {
    fun inspect(bytes: ByteArray): OnnxInspectionResult
}

/** Satu sebab model ditolak, dengan kode stabil untuk teks tampilan (D-03). */
data class OnnxInspectionProblem(
    val code: OnnxInspectionCode,
    val detail: String? = null,
)

enum class OnnxInspectionCode {
    /** Berkas kosong atau tidak dapat dibaca. */
    EMPTY_FILE,

    /** Berkas melebihi batas ukuran yang diberikan pemanggil. */
    FILE_TOO_LARGE,

    /** Berkas tidak dapat dibuka sebagai model ONNX. */
    UNREADABLE_MODEL,

    /** Metadata kelas (`names`) tidak ada atau tidak dapat dibaca. */
    MISSING_CLASS_NAMES,

    /** Bentuk tensor masukan bukan gambar empat dimensi yang didukung. */
    UNSUPPORTED_INPUT_SHAPE,

    /** Bentuk tensor keluaran tidak cocok dengan jumlah kelas model. */
    UNSUPPORTED_OUTPUT_SHAPE,
}
