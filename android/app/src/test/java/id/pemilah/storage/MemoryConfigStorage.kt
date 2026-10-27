package id.pemilah.storage

/**
 * Penyimpanan tiruan untuk unit test, dipakai bersama `ConfigStoreTest` dan
 * `ProfileRepositoryTest`.
 *
 * Aturan `listNames` di sini menyalin kontrak `ConfigStorage`: hanya berkas
 * lengkap yang terdaftar, berkas sementara `.tmp` dilewati, hasil urut menaik.
 * Aturan itu ditulis sekali supaya kedua test tidak menyimpang. Berkas biner
 * disimpan terpisah supaya isi teks dan isi model tidak tercampur.
 */
open class MemoryConfigStorage : ConfigStorage {
    val files = mutableMapOf<String, String>()
    val binaryFiles = mutableMapOf<String, ByteArray>()
    var failWrite = false
    val replaced = mutableListOf<Pair<String, String>>()

    override fun read(name: String): String? = files[name]

    override fun write(name: String, content: String) {
        if (failWrite) error("disk penuh")
        files[name] = content
    }

    override fun readBytes(name: String): ByteArray? = binaryFiles[name]

    override fun writeBytes(name: String, content: ByteArray) {
        if (failWrite) error("disk penuh")
        binaryFiles[name] = content
    }

    override fun replace(from: String, to: String) {
        replaced.add(from to to)
        val text = files[from]
        val binary = binaryFiles[from]
        when {
            binary != null -> {
                binaryFiles[to] = binary
                binaryFiles.remove(from)
            }

            text != null -> {
                files[to] = text
                files.remove(from)
            }

            else -> return
        }
    }

    override fun exists(name: String): Boolean =
        files.containsKey(name) || binaryFiles.containsKey(name)

    override fun delete(name: String): Boolean {
        if (failWrite) error("disk penuh")
        files.remove(name)
        binaryFiles.remove(name)
        return true
    }

    override fun listNames(prefix: String): List<String> = (files.keys + binaryFiles.keys)
        .filter { it.startsWith(prefix) && !it.endsWith(".tmp") }
        .distinct()
        .sorted()
}
