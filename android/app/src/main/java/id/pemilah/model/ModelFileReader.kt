package id.pemilah.model

/**
 * Pembaca berkas model yang dipilih operator (D-09, MOD-01).
 *
 * Abstraksi ini memisahkan pemilih dokumen Android dari alur impor supaya alur
 * impor dapat diuji tanpa perangkat (`Styleguide.md` §4). Implementasi nyata
 * memakai `ContentResolver` pada `id.pemilah.storage.ContentModelReader`.
 */
interface ModelFileReader {
    /** Nama tampilan berkas dari pemilih dokumen, atau `null` bila tidak ada. */
    fun displayName(uri: String): String?

    /** Isi berkas, atau `null` bila berkas tidak dapat dibaca. */
    fun readBytes(uri: String): ByteArray?
}
