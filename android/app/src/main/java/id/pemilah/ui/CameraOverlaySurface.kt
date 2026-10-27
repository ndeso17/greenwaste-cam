package id.pemilah.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Black tint bounds the worst-case white-camera background independently of blur support. */
internal const val CameraOverlayScrimAlpha = 0.64f

/** Only the sampled image is blurred; labels and controls are drawn in a separate layer. */
@Composable
internal fun CameraOverlaySurface(
    backdrop: ImageBitmap?,
    cameraBounds: Rect,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .onGloballyPositioned { bounds = it.boundsInRoot() },
    ) {
        Canvas(Modifier.matchParentSize().blur(14.dp)) {
            val image = backdrop
            if (image != null && cameraBounds.width > 0 && cameraBounds.height > 0 &&
                bounds.width > 0 && bounds.height > 0
            ) {
                // Pratinjau memakai FILL_CENTER: gambar diskalakan menutupi bingkai kamera
                // lalu dipotong di tengah. Sumber cuplikan dihitung dari transformasi isi
                // yang sama dengan overlay deteksi, bukan dari letterbox.
                val shown = fillRect(
                    frameAspect = image.width.toFloat() / image.height,
                    containerWidth = cameraBounds.width,
                    containerHeight = cameraBounds.height,
                )
                if (shown.width > 0f && shown.height > 0f) {
                    val x = ((bounds.left - cameraBounds.left - shown.left) / shown.width * image.width)
                        .roundToInt().coerceIn(0, image.width - 1)
                    val y = ((bounds.top - cameraBounds.top - shown.top) / shown.height * image.height)
                        .roundToInt().coerceIn(0, image.height - 1)
                    val width = (bounds.width / shown.width * image.width).roundToInt()
                        .coerceIn(1, image.width - x)
                    val height = (bounds.height / shown.height * image.height).roundToInt()
                        .coerceIn(1, image.height - y)
                    drawImage(
                        image, srcOffset = IntOffset(x, y), srcSize = IntSize(width, height),
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                }
            }
        }
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = CameraOverlayScrimAlpha)))
        content()
    }
}
