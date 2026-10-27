package id.pemilah.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import id.pemilah.R
import id.pemilah.storage.SampleRecord
import id.pemilah.storage.SampleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Jarak ketuk maksimum ke titik objek; sasaran sentuh tetap lebih besar dari titiknya. */
private val MarkerHitRadius = 28.dp

/**
 * Gambar sampel dengan kotak dan titik bantu bernomor (instruksi pengguna 27 Okt
 * 2026). Ketukan pada titik memilih objek; ketukan di luar titik tidak mengubah
 * pilihan supaya koreksi tidak salah sasaran.
 */
@Composable
internal fun GalleryImage(
    store: SampleStore,
    record: SampleRecord,
    modifier: Modifier = Modifier,
    selectedObjectIds: Set<String> = emptySet(),
    focusObjectId: String? = null,
    onObjectClick: ((String) -> Unit)? = null,
) {
    var bitmap by remember(record.id) { mutableStateOf<Bitmap?>(null) }
    var loaded by remember(record.id) { mutableStateOf(false) }
    var container by remember(record.id) { mutableStateOf(IntSize.Zero) }
    val hitRadiusPx = with(LocalDensity.current) { MarkerHitRadius.toPx() }
    LaunchedEffect(record.id) {
        bitmap = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(
                store.imageFile(record.id).path,
                BitmapFactory.Options().apply {
                    inSampleSize = (maxOf(record.width, record.height) / 1000).coerceAtLeast(1)
                },
            )
        }
        loaded = true
    }
    val aspect = record.width.toFloat() / record.height
    val detections = record.objects.mapIndexed { index, obj ->
        DetectionUi(
            box = NormalizedBox(obj.left, obj.top, obj.right, obj.bottom),
            classLabel = obj.correctedLabel ?: obj.predictedLabel,
            confidence = obj.probability,
            stale = false,
            marker = (index + 1).toString(),
            highlighted = obj.id in selectedObjectIds || obj.id == focusObjectId,
        )
    }
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .onSizeChanged { container = it }
            .then(
                if (onObjectClick == null) Modifier else Modifier.pointerInput(record.id, record.objects) {
                    detectTapGestures { offset ->
                        val width = container.width.toFloat()
                        val height = container.height.toFloat()
                        if (width <= 0f || height <= 0f) return@detectTapGestures
                        val nearest = record.objects
                            .map { obj ->
                                val (x, y) = objectAnchor(
                                    NormalizedBox(obj.left, obj.top, obj.right, obj.bottom),
                                )
                                obj to hypot(x * width - offset.x, y * height - offset.y)
                            }
                            .filter { (_, distance) -> distance <= hitRadiusPx }
                            .minByOrNull { (_, distance) -> distance }
                        nearest?.let { (obj, _) -> onObjectClick(obj.id) }
                    }
                },
            ),
    ) {
        bitmap?.let { Image(it.asImageBitmap(), record.id, Modifier.fillMaxSize()) }
            ?: androidx.compose.material3.Text(
                text = stringResource(if (loaded) R.string.sample_loading_error else R.string.gallery_loading),
                modifier = Modifier.fillMaxSize(),
            )
        DetectionOverlay(
            workArea = null,
            blocks = emptyList(),
            detections = detections,
            target = null,
        )
    }
}
