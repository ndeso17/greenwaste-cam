package id.pemilah.camera

import id.pemilah.inference.ArgbFrame

/**
 * Konversi buffer RGBA dari `ImageAnalysis` menjadi [ArgbFrame].
 *
 * Dipisah dari analyzer supaya dapat diuji tanpa perangkat: `rowStride` dan
 * `pixelStride` adalah nilai yang dilaporkan `ImageProxy`, dan padding baris
 * harus dihormati agar gambar tidak miring.
 */
object RgbaConversion {

    /**
     * Baca [width]×[height] piksel dari [bytes] (urutan RGBA, 4 byte per piksel).
     * `null` bila buffer terlalu pendek untuk ukuran yang diminta.
     */
    fun toArgbFrame(
        bytes: ByteArray,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int = 4,
    ): ArgbFrame? {
        if (width <= 0 || height <= 0 || pixelStride < 4) return null
        val lastByte = (height - 1) * rowStride + (width - 1) * pixelStride + 4
        if (bytes.size < lastByte) return null
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                val index = rowStart + x * pixelStride
                val red = bytes[index].toInt() and 0xFF
                val green = bytes[index + 1].toInt() and 0xFF
                val blue = bytes[index + 2].toInt() and 0xFF
                pixels[y * width + x] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
            }
        }
        return ArgbFrame(pixels, width, height)
    }
}
