package id.pemilah.ui

import kotlin.math.max
import kotlin.math.min

/** Lebar/tinggi kotak; nol bila kotak kosong. */
internal val NormalizedBox.width: Float get() = (right - left).coerceAtLeast(0f)
internal val NormalizedBox.height: Float get() = (bottom - top).coerceAtLeast(0f)

/**
 * Titik bantu di tengah kotak ternormalisasi (V-01, V-04).
 *
 * Titik ini dihitung dari geometri objek yang tersimpan, bukan ditebak Composable,
 * sehingga penanda pada pratinjau kamera dan pada gambar Galeri memakai sumber yang
 * sama. Kotak yang memuat beberapa objek tetap memakai satu titik; operator
 * mengoreksi per kotak, bukan per piksel.
 */
fun objectAnchor(box: NormalizedBox): Pair<Float, Float> =
    ((box.left + box.right) / 2f).coerceIn(0f, 1f) to
        ((box.top + box.bottom) / 2f).coerceIn(0f, 1f)

/** Kotak tampilan di dalam kontainer; `left/top/right/bottom` dalam piksel kontainer. */
data class FitRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Petakan titik ternormalisasi 0..1 gambar ke koordinat kontainer. */
    fun map(x: Float, y: Float): Pair<Float, Float> = left + x * width to top + y * height
}

/**
 * Kotak terbesar dengan aspek [aspect] (lebar/tinggi) yang muat di [containerWidth] x
 * [containerHeight], terpusat. `aspect` tidak sah menghasilkan kotak kosong, bukan
 * asumsi diam-diam.
 */
fun fitRect(aspect: Float, containerWidth: Float, containerHeight: Float): FitRect {
    if (!aspect.isFinite() || aspect <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
        return FitRect(0f, 0f, 0f, 0f)
    }
    val width = min(containerWidth, containerHeight * aspect)
    val height = width / aspect
    val left = (containerWidth - width) / 2f
    val top = (containerHeight - height) / 2f
    return FitRect(left, top, left + width, top + height)
}

/**
 * Petakan kotak ternormalisasi gambar ke koordinat layar untuk pratinjau `FILL_CENTER`
 * (V-03): gambar diskalakan sampai menutupi kontainer lalu dipotong di tengah, jadi
 * tidak ada pita hitam. Hasil boleh melewati batas kontainer — pemanggil memotongnya
 * lewat kanvas.
 *
 * [frameAspect] adalah lebar/tinggi frame seperti yang ditampilkan (sudah tegak).
 */
fun fillRect(frameAspect: Float, containerWidth: Float, containerHeight: Float): FitRect {
    if (!frameAspect.isFinite() || frameAspect <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
        return FitRect(0f, 0f, containerWidth.coerceAtLeast(0f), containerHeight.coerceAtLeast(0f))
    }
    val containerAspect = containerWidth / containerHeight
    val width = if (frameAspect >= containerAspect) containerHeight * frameAspect else containerWidth
    val height = if (frameAspect >= containerAspect) containerHeight else containerWidth / frameAspect
    val left = (containerWidth - width) / 2f
    val top = (containerHeight - height) / 2f
    return FitRect(left, top, left + max(width, 0f), top + max(height, 0f))
}
