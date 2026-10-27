package id.pemilah.ui

import id.pemilah.storage.BatchResult

/** Hasil aksi massal Galeri yang ditampilkan ke operator. */
internal sealed interface BatchNotice {
    data class Applied(val count: Int) : BatchNotice
    data class Failed(val ids: List<String>) : BatchNotice

    companion object {
        fun of(result: BatchResult): BatchNotice =
            if (result.isComplete) Applied(result.applied) else Failed(result.failed)
    }
}
