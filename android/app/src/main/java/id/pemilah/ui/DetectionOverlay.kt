package id.pemilah.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette

/** Kotak ternormalisasi 0..1 terhadap frame yang sudah ditransformasi kalibrasi. */
data class NormalizedBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Poligon ternormalisasi untuk area kerja. */
data class NormalizedPolygon(val points: List<Pair<Float, Float>>)

/** Satu deteksi untuk overlay; kelas dan keyakinan selalu ditampilkan bersama. */
data class DetectionUi(
    val box: NormalizedBox,
    val classLabel: String,
    val confidence: Float,
    val stale: Boolean,
)

/** Satu blok kerja terkonfigurasi. */
data class BlockUi(val id: String, val box: NormalizedBox)

/**
 * Overlay kamera sesuai urutan lapisan `.work/OperationScreen.xml`:
 * area kerja (putus-putus) → blok (putus-putus + label) → deteksi (garis penuh,
 * kelas + keyakinan, usang ditandai) → target aktif (sudut tebal + label TARGET).
 *
 * Semua geometri memakai koordinat ternormalisasi hasil transformasi yang sama
 * dengan pratinjau (CAM-02, CAM-03, CAL-01). Daftar kosong berarti tidak ada yang
 * digambar — overlay tidak pernah mengarang kotak atau blok.
 */
@Composable
fun DetectionOverlay(
    workArea: NormalizedPolygon?,
    blocks: List<BlockUi>,
    detections: List<DetectionUi>,
    target: NormalizedBox?,
    modifier: Modifier = Modifier,
    showStaleMark: Boolean = true,
    targetLabel: String? = null,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier.fillMaxSize()) {
        workArea?.let { polygon ->
            val path = androidx.compose.ui.graphics.Path().apply {
                polygon.points.firstOrNull()?.let { (x, y) ->
                    moveTo(x * size.width, y * size.height)
                    polygon.points.drop(1).forEach { (px, py) ->
                        lineTo(px * size.width, py * size.height)
                    }
                    close()
                }
            }
            drawPath(
                path = path,
                color = Palette.onSurfaceMuted,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                ),
            )
        }

        blocks.forEach { block ->
            val rect = block.box.toRect(size.width, size.height)
            drawRect(
                color = Palette.onSurfaceMuted.copy(alpha = 0.7f),
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                ),
            )
            drawLabel(
                measurer = measurer,
                text = block.id,
                anchor = rect.topLeft,
                color = Palette.onSurfaceMuted,
            )
        }

        detections.forEach { detection ->
            val rect = detection.box.toRect(size.width, size.height)
            val color = if (detection.stale && showStaleMark) Palette.warning else Palette.onSurface
            drawRect(
                color = color,
                topLeft = rect.topLeft,
                size = rect.size,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            drawLabel(
                measurer = measurer,
                text = buildString {
                    append(detection.classLabel)
                    append(' ')
                    append((detection.confidence * 100).toInt())
                    append('%')
                    if (detection.stale && showStaleMark) append(" (usang)")
                },
                anchor = Offset(rect.topLeft.x, rect.topLeft.y),
                color = color,
                above = true,
            )
        }

        target?.let { box ->
            val rect = box.toRect(size.width, size.height)
            val corner = minOf(rect.size.width, rect.size.height) * 0.22f
            val width = 3.dp.toPx()
            drawLine(Palette.accent, rect.topLeft, rect.topLeft + Offset(corner, 0f), width)
            drawLine(Palette.accent, rect.topLeft, rect.topLeft + Offset(0f, corner), width)
            drawLine(Palette.accent, rect.topRight, rect.topRight - Offset(corner, 0f), width)
            drawLine(Palette.accent, rect.topRight, rect.topRight + Offset(0f, corner), width)
            drawLine(Palette.accent, rect.bottomLeft, rect.bottomLeft + Offset(corner, 0f), width)
            drawLine(Palette.accent, rect.bottomLeft, rect.bottomLeft - Offset(0f, corner), width)
            drawLine(Palette.accent, rect.bottomRight, rect.bottomRight - Offset(corner, 0f), width)
            drawLine(Palette.accent, rect.bottomRight, rect.bottomRight - Offset(0f, corner), width)
            if (targetLabel != null) {
                drawLabel(
                    measurer = measurer,
                    text = targetLabel,
                    anchor = Offset(rect.topLeft.x, rect.topLeft.y),
                    color = Palette.accent,
                    above = true,
                )
            }
        }
    }
}

private class SimpleRect(val topLeft: Offset, val size: Size) {
    val topRight: Offset get() = Offset(topLeft.x + size.width, topLeft.y)
    val bottomLeft: Offset get() = Offset(topLeft.x, topLeft.y + size.height)
    val bottomRight: Offset get() = Offset(topLeft.x + size.width, topLeft.y + size.height)
}

private fun NormalizedBox.toRect(width: Float, height: Float): SimpleRect = SimpleRect(
    topLeft = Offset(left * width, top * height),
    size = Size((right - left) * width, (bottom - top) * height),
)

private fun DrawScope.drawLabel(
    measurer: TextMeasurer,
    text: String,
    anchor: Offset,
    color: Color,
    above: Boolean = false,
) {
    val layout = measurer.measure(
        text = text,
        style = TextStyle(fontSize = 11.sp, color = color),
    )
    val y = if (above) (anchor.y - layout.size.height).coerceAtLeast(0f) else anchor.y
    drawText(textLayoutResult = layout, topLeft = Offset(anchor.x, y))
}
