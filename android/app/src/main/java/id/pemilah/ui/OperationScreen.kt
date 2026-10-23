package id.pemilah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.camera.CameraPreview
import id.pemilah.camera.CameraStatus

/**
 * Layar operasi tahap 3: pratinjau kamera dominan di kiri, panel status di kanan
 * (Design.md §4.1, D-UI-01). Panel mesin belum terintegrasi dan menyatakannya
 * apa adanya; tidak ada angka atau status siap palsu.
 */
@Composable
fun OperationScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Palette.surfaceRaised, RoundedCornerShape(12.dp)),
            ) {
                CameraPreview(
                    hasPermission = hasPermission,
                    modifier = Modifier.fillMaxSize(),
                    onStatusChange = { cameraStatus = it },
                )
                CameraStatusOverlay(
                    status = cameraStatus,
                    hasPermission = hasPermission,
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
            SessionSummaryPlaceholder()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel(title = stringResource(R.string.panel_target)) {
                Text(
                    text = stringResource(R.string.target_none),
                    color = Palette.onSurfaceMuted,
                    fontSize = 14.sp,
                )
            }
            Panel(title = stringResource(R.string.panel_robot)) {
                Text(
                    text = stringResource(R.string.stage3_not_integrated),
                    color = Palette.onSurfaceMuted,
                    fontSize = 14.sp,
                )
            }
            Panel(title = stringResource(R.string.panel_conveyor)) {
                Text(
                    text = stringResource(R.string.stage3_not_integrated),
                    color = Palette.onSurfaceMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun CameraStatusOverlay(
    status: CameraStatus,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
) {
    val message = when (status) {
        CameraStatus.Ready -> null
        CameraStatus.Starting -> stringResource(R.string.camera_starting)
        CameraStatus.PermissionRequired -> stringResource(R.string.camera_permission_required)
        CameraStatus.NoCameraAvailable -> stringResource(R.string.camera_not_available)
        is CameraStatus.Failed -> stringResource(R.string.camera_failed, status.cause)
    } ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            color = Palette.warning,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (!hasPermission) {
            androidx.compose.material3.TextButton(onClick = onRequestPermission) {
                Text(text = stringResource(R.string.action_grant_camera), color = Palette.accent)
            }
        }
    }
}

@Composable
private fun SessionSummaryPlaceholder() {
    Panel(title = stringResource(R.string.panel_session)) {
        Text(
            text = stringResource(R.string.session_empty),
            color = Palette.onSurfaceMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.surfaceRaised, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            color = Palette.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}
