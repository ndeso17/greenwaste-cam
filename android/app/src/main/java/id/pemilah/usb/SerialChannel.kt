package id.pemilah.usb

/** Parameter link serial ke controller; nilai berasal dari konfigurasi, bukan kode. */
data class UsbLinkConfig(
    val baudRate: Int,
    val readTimeoutMs: Int,
    val writeTimeoutMs: Int,
)

/**
 * Kanal byte ke controller. Abstraksi ini memisahkan protokol (`CommandCodec`)
 * dari transport sehingga driver USB dapat diganti tanpa menyentuh protokol
 * (Architecture.md §3, §7).
 *
 * Kanal tidak menjaga batas pesan: satu pesan dapat terpecah atau tergabung
 * dalam satu pembacaan. Framing adalah tanggung jawab `CommandCodec` (B-04).
 */
interface SerialChannel {
    /** Menulis seluruh [bytes]; mengembalikan jumlah byte terkirim. */
    fun write(bytes: ByteArray, timeoutMs: Int): Int

    /** Membaca sampai [buffer] penuh atau timeout; mengembalikan jumlah byte. */
    fun read(buffer: ByteArray, timeoutMs: Int): Int

    /** Menutup kanal dan melepaskan resource USB. Aman dipanggil berulang. */
    fun close()
}

/** Pembuka kanal untuk perangkat yang sudah memperoleh izin USB. */
fun interface SerialChannelOpener {
    /**
     * Membuka kanal atau melempar [SerialOpenException] dengan alasan spesifik.
     */
    fun open(): SerialChannel
}

class SerialOpenException(val reason: SerialOpenReason, cause: Throwable? = null) :
    Exception(reason.name, cause)

enum class SerialOpenReason {
    PERMISSION_MISSING,
    DEVICE_GONE,
    UNSUPPORTED_DRIVER,
    LINE_CODING_FAILED,
    PORT_OPEN_FAILED,
}
