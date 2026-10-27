package id.pemilah.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import id.pemilah.inference.ArgbFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Jarak minimum antar-frame yang dikirim untuk klasifikasi (D-13). */
private const val FRAME_INTERVAL_MS: Long = 400L

/**
 * Status kamera yang ditampilkan ke operator (CAM-01, NFR-05). Setiap kegagalan
 * membawa penyebab dan langkah berikut, bukan pesan generik (Styleguide.md §5).
 */
sealed interface CameraStatus {
    data object Starting : CameraStatus
    data object Ready : CameraStatus
    data object PermissionRequired : CameraStatus
    data object NoCameraAvailable : CameraStatus
    data class Failed(val cause: String) : CameraStatus
}

/**
 * Pratinjau kamera CameraX (D-01). Komponen ini membuka/menutup kamera,
 * melaporkan status, dan menyalurkan frame ke [onFrame] bila pemanggil
 * menghendaki inferensi. Keputusan inferensi dan isi hasilnya bukan tugas
 * komponen ini (Architecture.md §3, D-13).
 *
 * `ImageAnalysis` hanya terpasang saat [onFrame] tidak `null`, sehingga tidak ada
 * frame yang diproses diam-diam ketika pemantauan berhenti.
 */
@Composable
fun CameraPreview(
    hasPermission: Boolean,
    modifier: Modifier = Modifier,
    onStatusChange: (CameraStatus) -> Unit = {},
    onBackdropFrame: ((Bitmap) -> Unit)? = null,
    onFrame: ((ArgbFrame) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var status by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView follows Compose bounds when the sidebar changes camera size.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val backdropCallback by rememberUpdatedState(onBackdropFrame)
    // Presentation-only samples. No analyzer, model or detection data is involved.
    LaunchedEffect(hasPermission, lifecycleOwner, previewView) {
        if (hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (backdropCallback != null && previewView.width > 0 && previewView.height > 0) {
                        previewView.bitmap?.let { source ->
                            val divisor = (maxOf(source.width, source.height) / 320f).coerceAtLeast(1f)
                            val small = Bitmap.createScaledBitmap(
                                source, (source.width / divisor).toInt().coerceAtLeast(1),
                                (source.height / divisor).toInt().coerceAtLeast(1), true,
                            )
                            if (small !== source) source.recycle()
                            backdropCallback?.invoke(small)
                        }
                    }
                    delay(600)
                }
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) {
            status = CameraStatus.PermissionRequired
        }
    }

    DisposableEffect(hasPermission, lifecycleOwner, onFrame != null) {
        var provider: ProcessCameraProvider? = null
        val future = if (hasPermission) ProcessCameraProvider.getInstance(context) else null
        future?.addListener(
            {
                val cameraProvider = runCatching { future.get() }.getOrElse { error ->
                    status = CameraStatus.Failed(error.message ?: error::class.java.simpleName)
                    onStatusChange(status)
                    return@addListener
                }
                provider = cameraProvider
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val selector = CameraSelector.DEFAULT_BACK_CAMERA
                status = if (!cameraProvider.hasCamera(selector)) {
                    CameraStatus.NoCameraAvailable
                } else {
                    runCatching {
                        cameraProvider.unbindAll()
                        val useCases = buildList {
                            add(preview)
                            if (onFrame != null) {
                                add(
                                    ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                                        )
                                        .setOutputImageFormat(
                                            ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888,
                                        )
                                        .build()
                                        .also { analysis ->
                                            analysis.setAnalyzer(analyzerExecutor) { image ->
                                                analyzeFrame(image, onFrame)
                                            }
                                        },
                                )
                            }
                        }
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            *useCases.toTypedArray(),
                        )
                        CameraStatus.Ready
                    }.getOrElse { error ->
                        CameraStatus.Failed(error.message ?: error::class.java.simpleName)
                    }
                }
                onStatusChange(status)
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

/**
 * Pemroses frame latar untuk `ImageAnalysis` (D-13).
 *
 * Aturan yang dijaga:
 * - `ImageProxy` selalu ditutup, termasuk saat frame dilewati, supaya kamera
 *   tidak berhenti karena buffer penuh.
 * - Hanya satu frame dikirim pada satu waktu; frame berikutnya dilewati sampai
 *   pemanggil selesai. Ini mencegah antrean inferensi menumpuk di perangkat
 *   lambat.
 * - Jarak minimum [FRAME_INTERVAL_MS] membatasi beban CPU; ResNet18 pada CPU
 *   perangkat tidak mampu memproses setiap frame.
 */
private fun analyzeFrame(image: ImageProxy, onFrame: ((ArgbFrame) -> Unit)?) {
    image.use { proxy ->
        if (onFrame == null) return
        val plane = proxy.planes.firstOrNull() ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < FRAME_INTERVAL_MS) return
        if (!frameBusy.compareAndSet(false, true)) return
        lastFrameAt = now
        val frame = try {
            RgbaConversion.toArgbFrame(
                bytes = plane.buffer.let { buffer ->
                    ByteArray(buffer.remaining()).also { buffer.get(it) }
                },
                width = proxy.width,
                height = proxy.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
            )
        } finally {
            // Frame yang gagal dikonversi tidak boleh menahan status sibuk.
            frameBusy.set(false)
        }
        if (frame == null) return
        onFrame(frame)
        frameBusy.set(false)
    }
}

private var lastFrameAt: Long = 0L
private val frameBusy = AtomicBoolean(false)
private val analyzerExecutor = Executors.newSingleThreadExecutor()
