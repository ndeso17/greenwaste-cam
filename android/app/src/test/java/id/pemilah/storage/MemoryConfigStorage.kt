package id.pemilah.storage

/**
 * Penyimpanan tiruan untuk unit test, dipakai bersama `ConfigStoreTest` dan
 * `ProfileRepositoryTest`.
 *
 * Aturan `listNames` di sini menyalin kontrak `ConfigStorage`: hanya berkas
 * lengkap yang terdaftar, berkas sementara `.tmp` dilewati, hasil urut menaik.
 * Aturan itu ditulis sekali supaya kedua test tidak menyimpang.
 */
open class MemoryConfigStorage : ConfigStorage {
    val files = mutableMapOf<String, String>()
    var failWrite = false
    val replaced = mutableListOf<Pair<String, String>>()

    override fun read(name: String): String? = files[name]

    override fun write(name: String, content: String) {
        if (failWrite) error("disk penuh")
        files[name] = content
    }

    override fun replace(from: String, to: String) {
        replaced.add(from to to)
        files[to] = files[from] ?: return
        files.remove(from)
    }

    override fun exists(name: String): Boolean = files.containsKey(name)

    override fun listNames(prefix: String): List<String> = files.keys
        .filter { it.startsWith(prefix) && !it.endsWith(".tmp") }
        .sorted()
}
