package id.pemilah.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import id.pemilah.camera.CameraStatus
import id.pemilah.inference.*

/** Reuses the actual operation surface so layout and camera controls stay consistent. */
@Composable
fun ModelTestScreen(
    state: OperationUiState,
    classification: ClassificationUi,
    detections: List<TileDetection>,
    detectionsAtMillis: Long,
    frameAspectRatio: Float?,
    saved: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFrame: (ArgbFrame) -> Unit,
    onCameraStatusChange: (CameraStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    OperationScreen(modifier,state,classification,detections,detectionsAtMillis,
        onStart,onStop,onFrame,onCameraStatusChange,testMode=true,frameAspectRatio=frameAspectRatio,savedSamples=saved)
}
