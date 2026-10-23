package id.pemilah.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Transport USB OTG ke ESP32-S3 lewat native USB TinyUSB **CDC-ACM** (D-04).
 *
 * Batasan yang dijaga:
 * - Hanya perangkat dengan antarmuka CDC Data yang diterima; perangkat lain
 *   ditolak dengan [SerialOpenReason.UNSUPPORTED_DRIVER] alih-alih dicoba
 *   dengan driver yang salah.
 * - DTR dan RTS dipaksa nonaktif setelah port dibuka, agar rangkaian auto-reset
 *   pada board bridge tidak menahan MCU di mode bootloader.
 * - Kanal tidak menjaga batas pesan; framing milik `CommandCodec` (B-04).
 *
 * Berkas ini bergantung pada Android framework sehingga tidak diuji dengan unit
 * test; perilaku yang dapat diuji berada di `ControllerConnection`.
 */
class UsbCdcChannelOpener(
    private val context: Context,
    private val config: UsbLinkConfig,
) {

    private val manager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    /** Perangkat CDC-ACM yang terpasang, terurut agar pilihan stabil. */
    fun candidates(): List<UsbDevice> {
        val usbManager = manager ?: return emptyList()
        return usbManager.deviceList.values
            .filter { isCdcAcm(it) }
            .sortedBy { it.deviceId }
    }

    fun isCdcAcm(device: UsbDevice): Boolean =
        (0 until device.interfaceCount).any { index ->
            device.getInterface(index).interfaceClass == UsbConstants.USB_CLASS_CDC_DATA
        }

    fun hasPermission(device: UsbDevice): Boolean =
        manager?.hasPermission(device) == true

    /** Minta izin USB; hasilnya dikirim lewat broadcast [ACTION_USB_PERMISSION]. */
    fun requestPermission(device: UsbDevice): Boolean {
        val usbManager = manager ?: return false
        usbManager.requestPermission(device, permissionIntent(context.packageName))
        return true
    }

    fun open(device: UsbDevice): SerialChannel {
        val usbManager = manager ?: throw SerialOpenException(SerialOpenReason.DEVICE_GONE)
        if (!usbManager.hasPermission(device)) {
            throw SerialOpenException(SerialOpenReason.PERMISSION_MISSING)
        }
        if (!isCdcAcm(device)) {
            throw SerialOpenException(SerialOpenReason.UNSUPPORTED_DRIVER)
        }
        val connection = usbManager.openDevice(device)
            ?: throw SerialOpenException(SerialOpenReason.PORT_OPEN_FAILED)
        return try {
            val driver = CdcAcmSerialDriver(device)
            val port = driver.ports.firstOrNull()
                ?: throw SerialOpenException(SerialOpenReason.UNSUPPORTED_DRIVER)
            port.open(connection)
            port.setParameters(config.baudRate, DATA_BITS, STOP_BITS, PARITY)
            // Nonaktifkan DTR/RTS: default HIGH dapat menahan ESP32-S3 di reset.
            port.dtr = false
            port.rts = false
            UsbSerialChannel(port)
        } catch (error: SerialOpenException) {
            connection.close()
            throw error
        } catch (error: Exception) {
            connection.close()
            throw SerialOpenException(SerialOpenReason.LINE_CODING_FAILED, error)
        }
    }

    private fun permissionIntent(packageName: String): PendingIntent {
        // FLAG_MUTABLE wajib pada API 31+; immutable membuat extras izin tidak
        // pernah terisi sehingga dialog izin tidak muncul.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        return PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(packageName),
            flags,
        )
    }

    private class UsbSerialChannel(private val port: UsbSerialPort) : SerialChannel {

        override fun write(bytes: ByteArray, timeoutMs: Int): Int {
            port.write(bytes, timeoutMs)
            return bytes.size
        }

        override fun read(buffer: ByteArray, timeoutMs: Int): Int =
            port.read(buffer, timeoutMs)

        override fun close() {
            runCatching { port.close() }
        }
    }

    companion object {
        const val ACTION_USB_PERMISSION: String = "id.pemilah.action.USB_PERMISSION"

        /** CDC-ACM adalah 8-N-1; bagian dari standar CDC, bukan nilai mesin. */
        const val DATA_BITS: Int = 8
        const val STOP_BITS: Int = UsbSerialPort.STOPBITS_1
        const val PARITY: Int = UsbSerialPort.PARITY_NONE
    }
}
