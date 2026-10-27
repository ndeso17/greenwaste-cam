package id.pemilah.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bukti konversi buffer RGBA `ImageAnalysis` menjadi piksel ARGB.
 *
 * Yang diuji: urutan kanal, penghormatan `rowStride` (padding baris) dan
 * `pixelStride`, serta penolakan buffer yang terlalu pendek. Konversi ini titik
 * paling rawan salah pada jalur inferensi, sehingga diuji tanpa perangkat.
 */
class RgbaConversionTest {

    @Test
    fun toArgbFrame_convertsRedGreenBlue() {
        // Tiga piksel sebaris tanpa padding: merah, hijau, biru.
        val bytes = byteArrayOf(
            0xFF.toByte(), 0, 0, 0xFF.toByte(),
            0, 0xFF.toByte(), 0, 0xFF.toByte(),
            0, 0, 0xFF.toByte(), 0xFF.toByte(),
        )

        val frame = requireNotNull(
            RgbaConversion.toArgbFrame(bytes, width = 3, height = 1, rowStride = 12),
        )

        assertEquals(0xFFFF0000.toInt(), frame.pixels[0])
        assertEquals(0xFF00FF00.toInt(), frame.pixels[1])
        assertEquals(0xFF0000FF.toInt(), frame.pixels[2])
    }

    @Test
    fun toArgbFrame_respectsRowPadding() {
        // Satu piksel per baris, rowStride 8 (4 byte padding tiap baris).
        val bytes = byteArrayOf(
            0xFF.toByte(), 0, 0, 0xFF.toByte(), 1, 1, 1, 1,
            0, 0xFF.toByte(), 0, 0xFF.toByte(), 2, 2, 2, 2,
        )

        val frame = requireNotNull(
            RgbaConversion.toArgbFrame(bytes, width = 1, height = 2, rowStride = 8),
        )

        assertEquals(0xFFFF0000.toInt(), frame.pixels[0])
        assertEquals(0xFF00FF00.toInt(), frame.pixels[1])
    }

    @Test
    fun toArgbFrame_alphaIsAlwaysOpaque() {
        val bytes = byteArrayOf(10, 20, 30, 0)

        val frame = requireNotNull(
            RgbaConversion.toArgbFrame(bytes, width = 1, height = 1, rowStride = 4),
        )

        assertEquals(0xFF, (frame.pixels[0] shr 24) and 0xFF)
    }

    @Test
    fun toArgbFrame_shortBuffer_returnsNull() {
        val bytes = byteArrayOf(0, 0, 0, 0)

        assertNull(RgbaConversion.toArgbFrame(bytes, width = 2, height = 2, rowStride = 8))
    }

    @Test
    fun toArgbFrame_zeroSize_returnsNull() {
        assertNull(RgbaConversion.toArgbFrame(ByteArray(16), width = 0, height = 1, rowStride = 4))
        assertNull(RgbaConversion.toArgbFrame(ByteArray(16), width = 1, height = 0, rowStride = 4))
    }
}
