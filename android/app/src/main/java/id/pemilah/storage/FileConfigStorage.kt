package id.pemilah.storage

import android.content.Context
import java.io.File

/**
 * Penyimpanan berkas pada direktori privat aplikasi (NFR-03).
 *
 * Tidak diuji dengan unit test karena membutuhkan `Context`; perilaku penyimpanan
 * diuji lewat [ConfigStore] dengan [ConfigStorage] tiruan.
 */
class FileConfigStorage(context: Context) : ConfigStorage {

    private val directory: File = File(context.filesDir, "config").apply { mkdirs() }

    override fun read(name: String): String? {
        val file = File(directory, name)
        return if (file.exists()) file.readText() else null
    }

    override fun write(name: String, content: String) {
        File(directory, name).writeText(content)
    }

    override fun replace(from: String, to: String) {
        val source = File(directory, from)
        val target = File(directory, to)
        if (!source.renameTo(target)) {
            target.writeText(source.readText())
            source.delete()
        }
    }

    override fun exists(name: String): Boolean = File(directory, name).exists()

    /**
     * Daftar berkas lengkap berawalan [prefix] (MOD-01). Nama berkas sementara
     * berakhiran `.tmp` dilewati karena bisa berasal dari tulisan yang gagal di
     * tengah jalan dan isinya belum tentu utuh.
     */
    override fun listNames(prefix: String): List<String> {
        val files = directory.listFiles() ?: return emptyList()
        return files
            .asSequence()
            .filter { it.isFile }
            .map { it.name }
            .filter { it.startsWith(prefix) }
            .filterNot { it.endsWith(TEMPORARY_SUFFIX) }
            .sorted()
            .toList()
    }

    private companion object {
        const val TEMPORARY_SUFFIX: String = ".tmp"
    }
}
