package id.pemilah.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Garis bantu pratinjau kamera (instruksi pengguna 27 Oktober 2026).
 *
 * Garis ini **bukan** geometri mesin: bukan area kerja hasil kalibrasi, bukan
 * blok, dan tidak dipakai untuk menghitung koordinat objek. Fungsinya hanya
 * membantu operator menempatkan objek di tengah bidang pandang. Geometri mesin
 * tetap berasal dari kalibrasi (`Architecture.md` §6, B-03).
 *
 * Perhitungan posisi garis dipisah sebagai fungsi murni supaya dapat diuji tanpa
 * perangkat: `CameraGuides.thirds(width, height)`.
 */
object CameraGuides {

    /** Satu segmen garis lurus dalam koordinat piksel bidang gambar. */
    data class Segment(val startX: Float, val startY: Float, val endX: Float, val endY: Float)

    /**
     * Empat garis pembagi tiga: dua vertikal di 1/3 dan 2/3 lebar, dua horizontal
     * di 1/3 dan 2/3 tinggi. Hasil kosong bila bidang belum berukuran.
     */
    fun thirds(width: Float, height: Float): List<Segment> {
        if (width <= 0f || height <= 0f) return emptyList()
        val firstX = width / 3f
        val secondX = width * 2f / 3f
        val firstY = height / 3f
        val secondY = height * 2f / 3f
        return listOf(
            Segment(firstX, 0f, firstX, height),
            Segment(secondX, 0f, secondX, height),
            Segment(0f, firstY, width, firstY),
            Segment(0f, secondY, width, secondY),
        )
    }
}

/**
 * Menggambar [CameraGuides.thirds] di atas pratinjau. Putus-putus dan tipis
 * supaya objek tetap terlihat; warna putih dengan alfa sedang terbaca di
 * latar terang maupun gelap tanpa menjadi satu-satunya pembeda makna
 * (`Design.md` §7).
 */
@Composable
fun CameraGuidesOverlay(
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.45f),
) {
    if (!visible) return
    Canvas(modifier = modifier.fillMaxSize()) {
        drawGuides(CameraGuides.thirds(size.width, size.height), color)
    }
}

private fun DrawScope.drawGuides(segments: List<CameraGuides.Segment>, color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
    segments.forEach { segment ->
        drawLine(
            color = color,
            start = Offset(segment.startX, segment.startY),
            end = Offset(segment.endX, segment.endY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash,
        )
    }
    // Bingkai tipis menandai batas bidang pandang, bukan area kerja.
    drawRect(
        color = color,
        topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
        size = size.copy(
            width = (size.width - 2.dp.toPx()).coerceAtLeast(0f),
            height = (size.height - 2.dp.toPx()).coerceAtLeast(0f),
        ),
        style = Stroke(width = 1.dp.toPx()),
    )
}
