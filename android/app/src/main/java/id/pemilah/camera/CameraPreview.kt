package id.pemilah.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.camera.core.CameraSelector
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
 * Pratinjau kamera CameraX (D-01). Komponen ini hanya membuka/menutup kamera dan
 * melaporkan status; inferensi, overlay, dan pemilihan target bukan tugasnya
 * (Architecture.md §3).
 */
@Composable
fun CameraPreview(
    hasPermission: Boolean,
    modifier: Modifier = Modifier,
    onStatusChange: (CameraStatus) -> Unit = {},
    onBackdropFrame: ((Bitmap) -> Unit)? = null,
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

    DisposableEffect(hasPermission, lifecycleOwner) {
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
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
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
