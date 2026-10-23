package id.pemilah.usb

/** Status link ke controller (COM-01, COM-06); dikonsumsi `OperationGuard`. */
enum class LinkState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

enum class LinkFailure {
    PERMISSION_MISSING,
    DEVICE_GONE,
    UNSUPPORTED_DRIVER,
    LINE_CODING_FAILED,
    PORT_OPEN_FAILED,
    UNEXPECTED,
}

data class LinkStatus(
    val state: LinkState,
    val failure: LinkFailure? = null,
) {
    val isConnected: Boolean get() = state == LinkState.CONNECTED

    companion object {
        val disconnected = LinkStatus(LinkState.DISCONNECTED)
    }
}

/**
 * Pemilik status link ke controller (WP-26).
 *
 * Aturan yang dijaga:
 * - Hanya satu kanal aktif pada satu waktu; membuka ulang menutup kanal lama.
 * - Putus koneksi **tidak** memulai ulang operasi otomatis; status berubah ke
 *   [LinkState.DISCONNECTED] dan `OperationGuard` yang memutuskan langkah
 *   berikutnya (WP-51, Rules.md §2.8/§2.9).
 * - Kegagalan pembukaan dilaporkan sebagai [LinkStatus] dengan alasan, bukan
 *   eksepsi ke pemanggil UI.
 *
 * Kelas ini tidak memegang Android framework sehingga dapat diuji langsung.
 */
class ControllerConnection(private val opener: SerialChannelOpener) {

    private var channel: SerialChannel? = null

    var status: LinkStatus = LinkStatus.disconnected
        private set

    val currentChannel: SerialChannel? get() = channel

    fun connect(): LinkStatus {
        status = LinkStatus(LinkState.CONNECTING)
        closeChannelQuietly()
        status = try {
            channel = opener.open()
            LinkStatus(LinkState.CONNECTED)
        } catch (error: SerialOpenException) {
            LinkStatus(LinkState.FAILED, error.reason.toLinkFailure())
        } catch (error: Exception) {
            LinkStatus(LinkState.FAILED, LinkFailure.UNEXPECTED)
        }
        return status
    }

    /** Putuskan aktif (mis. debounce kabel) tanpa menyentuh status sebelumnya. */
    fun disconnect(): LinkStatus {
        closeChannelQuietly()
        status = LinkStatus.disconnected
        return status
    }

    /** Tandai putus setelah kegagalan I/O; kanal lama tidak dipakai lagi. */
    fun markLost(): LinkStatus {
        closeChannelQuietly()
        status = LinkStatus(LinkState.DISCONNECTED, LinkFailure.DEVICE_GONE)
        return status
    }

    private fun closeChannelQuietly() {
        runCatching { channel?.close() }
        channel = null
    }

    private fun SerialOpenReason.toLinkFailure(): LinkFailure = when (this) {
        SerialOpenReason.PERMISSION_MISSING -> LinkFailure.PERMISSION_MISSING
        SerialOpenReason.DEVICE_GONE -> LinkFailure.DEVICE_GONE
        SerialOpenReason.UNSUPPORTED_DRIVER -> LinkFailure.UNSUPPORTED_DRIVER
        SerialOpenReason.LINE_CODING_FAILED -> LinkFailure.LINE_CODING_FAILED
        SerialOpenReason.PORT_OPEN_FAILED -> LinkFailure.PORT_OPEN_FAILED
    }
}
