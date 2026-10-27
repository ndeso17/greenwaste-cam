package id.pemilah.storage

/** Hasil operasi massal: jumlah yang berhasil dan daftar target yang gagal. */
data class BatchResult(val applied: Int, val failed: List<String>) {
    val isComplete: Boolean get() = failed.isEmpty()
}

/** Satu objek yang akan dikoreksi: gambar [sampleId] dan objek [objectId]. */
data class CorrectionTarget(val sampleId: String, val objectId: String)
