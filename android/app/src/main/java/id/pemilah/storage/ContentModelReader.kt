package id.pemilah.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import id.pemilah.model.ModelFileReader

/**
 * Pembaca berkas model dari URI pemilih dokumen Android (D-09, MOD-01).
 *
 * Isi berkas dibaca menjadi byte lalu disalin ke penyimpanan aplikasi oleh
 * `ModelImport`; URI tidak pernah disimpan karena izin baca URI tidak dijamin
 * bertahan setelah proses aplikasi dibangun ulang.
 *
 * Kelas ini memerlukan `Context` sehingga tidak diuji unit; alur impor diuji
 * lewat `ModelImportTest` dengan pembaca tiruan. Perilaku pada perangkat nyata
 * dibuktikan pada `.work/evidence/models-onnx-import.md`.
 */
class ContentModelReader(private val context: Context) : ModelFileReader {

    override fun displayName(uri: String): String? = runCatching {
        context.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column < 0 || !cursor.moveToFirst()) return@use null
            cursor.getString(column)
        }
    }.getOrNull()

    override fun readBytes(uri: String): ByteArray? = runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            stream.readBytes()
        }
    }.getOrNull()
}
