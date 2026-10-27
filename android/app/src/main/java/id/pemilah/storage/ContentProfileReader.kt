package id.pemilah.storage

import android.content.Context
import android.net.Uri
import id.pemilah.model.ProfileFileReader

/**
 * Pembaca berkas profil dari URI pemilih dokumen Android (MOD-01).
 *
 * Isi berkas dibaca menjadi teks lalu disalin ke penyimpanan aplikasi oleh
 * `ProfileImport`; URI tidak pernah disimpan karena izin baca URI tidak dijamin
 * bertahan setelah proses aplikasi dibangun ulang.
 *
 * Kelas ini memerlukan `Context` sehingga tidak diuji unit; jalur impor diuji
 * lewat `ProfileImportTest` dengan pembaca tiruan. Perilaku pada perangkat nyata
 * termasuk MANUAL-VERIFY WP-13.
 */
class ContentProfileReader(private val context: Context) : ProfileFileReader {

    override fun read(uri: String): String? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }
    }.getOrNull()
}