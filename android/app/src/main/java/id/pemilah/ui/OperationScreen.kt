package id.pemilah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
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
import id.pemilah.usb.LinkState

/**
 * Layar operasi sesuai `.work/OperationScreen.xml` dan `Design.md` §4:
 * preview penuh → ringkasan glass di atas → status + shutter overlay di bawah.
 *
 * Aturan yang dijaga:
 * - Tahap 3 hanya memantau. Panel robot, tujuan bak, dan kecepatan menampilkan
 *   "Belum terintegrasi" beserta alasannya, bukan nilai atau status siap palsu.
 * - Tombol Mulai nonaktif selama guard gagal; alasannya tampil tepat di atas
 *   panel kanan sesuai revisi pengguna 27 Oktober 2026.
 * - Tidak ada angka contoh: metrik sesi kosong ditulis "—", kecepatan aktual
 *   kosong ditulis "Belum ada data".
 */
@Composable
fun OperationScreen(
    modifier: Modifier = Modifier,
    state: OperationUiState? = null,
    onOpenHistory: () -> Unit = {},
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    var panelVisible by rememberSaveable { mutableStateOf(true) }
    val panelScroll = rememberScrollState()
    var liveCameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    val ui = (state ?: rememberDefaultOperationState()).copy(cameraStatus = liveCameraStatus)
    val blockers = StartGuardProjection.blockers(ui)
    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        val sidebarWidth = if (maxWidth < 600.dp) {
            (maxWidth - 24.dp).coerceAtMost(300.dp)
        } else {
            (maxWidth * 0.36f).coerceAtMost(340.dp)
        }
        CameraStage(
            state = ui,
            onOpenHistory = onOpenHistory,
            onStart = onStart,
            onStop = onStop,
            cameraStatus = liveCameraStatus,
            onCameraStatusChange = { liveCameraStatus = it },
            modifier = Modifier.fillMaxSize(),
        )
        if (panelVisible) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 8.dp, bottom = 196.dp)
                    .width(sidebarWidth).fillMaxHeight()
                    .background(Palette.surface.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
                    .verticalScroll(panelScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                if (blockers.isNotEmpty() || ui.isRunning) {
                    GuardBanner(blockers = blockers, isRunning = ui.isRunning, stage = ui.stage)
                }
                TargetPanel(state = ui)
                RobotPanel(state = ui)
                ConveyorPanel(state = ui)
            }
        }
        val toggleLabel = stringResource(
            if (panelVisible) R.string.action_hide_panels else R.string.action_show_panels,
        )
        IconButton(
            onClick = { panelVisible = !panelVisible },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(48.dp)
                .background(Color.Black.copy(alpha = CameraOverlayScrimAlpha), RoundedCornerShape(24.dp))
                .statusDescription(toggleLabel),
        ) {
            Icon(if (panelVisible) Icons.Rounded.Close else Icons.Rounded.Tune,
                contentDescription = null, tint = Color.White)
        }
    }
}

/**
 * Keadaan awal layar: seluruh data belum tersedia. Nilai kamera diisi oleh
 * [CameraPreview] saat pratinjau berjalan; sisanya menunggu pemilik state.
 */
@Composable
fun rememberDefaultOperationState(): OperationUiState {
    val context = LocalContext.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    return remember { OperationUiState(hasCameraPermission = granted) }
}

@Composable
private fun CameraStatusStrip(state: OperationUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        CameraStatusField(
            label = stringResource(R.string.header_camera),
            value = stringResource(state.cameraStatus.labelRes()),
            tone = state.cameraStatus.tone(),
        )
        StatusInfoDivider()
        CameraStatusField(
            label = stringResource(R.string.header_calibration),
            value = stringResource(state.calibration.labelRes()),
            tone = state.calibration.tone(),
        )
        StatusInfoDivider()
        CameraStatusField(
            label = stringResource(R.string.header_usb),
            value = stringResource(state.linkState.labelRes()),
            tone = state.linkState.tone(),
        )
    }
}

@Composable
private fun CameraStatusField(label: String, value: String, tone: StatusTone) {
    Row(
        modifier = Modifier.statusDescription("$label: $value"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusIcon(tone)
        Column {
            Text(label, color = Color.White, fontSize = 10.sp)
            Text(value, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CameraStage(
    state: OperationUiState,
    onOpenHistory: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    cameraStatus: CameraStatus,
    onCameraStatusChange: (CameraStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    var cameraBounds by remember { mutableStateOf(Rect.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { cameraBounds = it.boundsInRoot() }
            .background(Palette.surface),
    ) {
        val context = LocalContext.current
        var permissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted -> permissionGranted = granted }

        CameraPreview(
            hasPermission = permissionGranted,
            modifier = Modifier.fillMaxSize(),
            onStatusChange = onCameraStatusChange,
            onBackdropFrame = { backdrop = it.asImageBitmap() },
        )
        // Overlay hanya menggambar data yang benar-benar ada; saat ini belum ada
        // geometri terkalibrasi sehingga tidak ada kotak atau blok yang dikarang.
        DetectionOverlay(
            workArea = null,
            blocks = emptyList(),
            detections = emptyList(),
            target = null,
            targetLabel = stringResource(R.string.target_label),
        )
        CameraStatusOverlay(
            status = cameraStatus,
            hasPermission = permissionGranted,
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
        )
        CameraOverlaySurface(
            backdrop = backdrop,
            cameraBounds = cameraBounds,
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = 8.dp, end = 64.dp, top = 8.dp).fillMaxWidth(),
        ) {
            SessionSummaryBar(state = state, onOpenHistory = onOpenHistory)
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CameraOverlaySurface(
                backdrop = backdrop,
                cameraBounds = cameraBounds,
                modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 640.dp).fillMaxWidth(),
            ) {
                CameraStatusStrip(state = state)
            }
            CameraShutterControls(state = state, onStart = onStart, onStop = onStop)
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
            TextButton(onClick = onRequestPermission) {
                Text(text = stringResource(R.string.action_grant_camera), color = Palette.accent)
            }
        }
    }
}

@Composable
private fun SessionSummaryBar(state: OperationUiState, onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.panel_session), color = Color.White, fontSize = 12.sp)
        val session = state.session
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SessionMetric(
                label = stringResource(R.string.session_motion_completed),
                fullLabel = stringResource(R.string.session_motion_completed_full),
                value = session?.motionCompleted,
                tone = StatusTone.MUTED,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_verified),
                fullLabel = stringResource(R.string.session_pickup_verified_full),
                value = session?.pickupVerified,
                tone = StatusTone.SUCCESS,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_unverified),
                fullLabel = stringResource(R.string.session_pickup_unverified_full),
                value = session?.pickupUnverified,
                tone = StatusTone.WARNING,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_failed),
                fullLabel = stringResource(R.string.session_pickup_failed_full),
                value = session?.pickupFailed,
                tone = StatusTone.DANGER,
            )
            SessionMetric(
                label = stringResource(R.string.session_queue),
                fullLabel = stringResource(R.string.session_queue_full),
                value = state.pendingEvents,
                tone = if (state.pendingEvents != null && state.pendingEvents > 0) {
                    StatusTone.WARNING
                } else {
                    StatusTone.MUTED
                },
            )
            CameraStatusField(
                label = stringResource(R.string.session_backend),
                value = stringResource(
                    when (state.backend) {
                        BackendUiState.CONNECTED -> R.string.session_backend_connected
                        BackendUiState.UNAVAILABLE -> R.string.session_backend_unavailable
                        BackendUiState.UNKNOWN -> R.string.session_value_none
                    },
                ),
                tone = when (state.backend) {
                    BackendUiState.CONNECTED -> StatusTone.SUCCESS
                    BackendUiState.UNAVAILABLE -> StatusTone.WARNING
                    BackendUiState.UNKNOWN -> StatusTone.MUTED
                },
            )
            TextButton(onClick = onOpenHistory) {
                Text(stringResource(R.string.session_open_history), color = Color.White)
            }
        }
    }
}

@Composable
private fun SessionMetric(
    label: String,
    fullLabel: String,
    value: Int?,
    tone: StatusTone,
) {
    // Singkatan tampil di layar; `fullLabel` menjadi deskripsi pembaca layar agar
    // makna status tidak hilang (Design.md §7: status wajib punya teks bermakna).
    Row(
        modifier = Modifier.statusDescription("$fullLabel: ${value ?: "-"}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusIcon(tone)
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Text(
            text = value?.toString() ?: stringResource(R.string.session_value_none),
            color = if (value == null) Color.White else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TargetPanel(state: OperationUiState) {
    MachinePanel(title = stringResource(R.string.panel_target)) {
        val target = state.activeTarget
        if (target == null) {
            PlaceholderText(text = stringResource(R.string.target_none))
            return@MachinePanel
        }
        PanelField(
            label = stringResource(R.string.target_field_id),
            value = target.id,
        )
        PanelField(
            label = stringResource(R.string.target_field_class),
            value = target.classLabel,
        )
        PanelField(
            label = stringResource(R.string.target_field_location),
            value = target.blockAndPosition ?: stringResource(R.string.session_value_none),
        )
        PanelField(
            label = stringResource(R.string.target_field_destination),
            value = if (state.stage == OperationStage.SORTING) {
                target.binAndRobot ?: stringResource(R.string.session_value_none)
            } else {
                stringResource(R.string.stage3_not_integrated)
            },
        )
    }
}

@Composable
private fun RobotPanel(state: OperationUiState) {
    MachinePanel(title = stringResource(R.string.panel_robot)) {
        if (state.stage == OperationStage.MONITORING) {
            PlaceholderText(text = stringResource(R.string.stage3_not_integrated))
            return@MachinePanel
        }
        RobotRow(label = stringResource(R.string.robot_left), robot = state.leftRobot)
        RobotRow(label = stringResource(R.string.robot_right), robot = state.rightRobot)
        HairlineDivider()
        PlaceholderText(text = stringResource(R.string.robot_rule))
    }
}

@Composable
private fun RobotRow(label: String, robot: RobotUiState?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = Palette.onSurfaceMuted, fontSize = 12.sp, modifier = Modifier.width(56.dp))
        if (robot == null) {
            PlaceholderText(text = stringResource(R.string.session_value_none))
        } else {
            StatusChip(
                label = "",
                value = stringResource(robot.labelRes()),
                tone = robot.tone(),
            )
        }
    }
}

@Composable
private fun ConveyorPanel(state: OperationUiState) {
    MachinePanel(title = stringResource(R.string.panel_conveyor)) {
        if (state.stage == OperationStage.MONITORING) {
            PlaceholderText(text = stringResource(R.string.stage3_not_integrated))
            return@MachinePanel
        }
        PanelField(
            label = stringResource(R.string.conveyor_requested),
            value = state.conveyor.requestedDraft ?: stringResource(R.string.session_value_none),
        )
        PanelField(
            label = stringResource(R.string.conveyor_actual),
            value = when {
                state.conveyor.actualEncoder == null -> stringResource(R.string.conveyor_no_data)
                state.conveyor.actualStale ->
                    stringResource(R.string.header_value_pattern, "", "") + state.conveyor.actualEncoder
                else -> state.conveyor.actualEncoder
            },
        )
        SecondaryAction(
            label = stringResource(R.string.conveyor_apply),
            onClick = {},
            enabled = false,
        )
        HairlineDivider()
        PlaceholderText(text = stringResource(R.string.conveyor_rule))
    }
}

@Composable
private fun GuardBanner(blockers: List<StartBlocker>, isRunning: Boolean, stage: OperationStage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (isRunning) {
            Text(
                text = stringResource(
                    if (stage == OperationStage.SORTING) {
                        R.string.operation_status_sorting_running
                    } else {
                        R.string.operation_mode_monitoring
                    },
                ),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.guard_banner_title),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        blockers.forEach { blocker ->
            Text(
                text = stringResource(blocker.messageRes()),
                color = Palette.onSurface,
                fontSize = 12.sp,
            )
        }
    }
}

private fun CameraStatus.labelRes(): Int = when (this) {
    CameraStatus.Ready -> R.string.header_camera_ready
    CameraStatus.Starting -> R.string.header_camera_starting
    CameraStatus.PermissionRequired -> R.string.header_camera_unavailable
    CameraStatus.NoCameraAvailable -> R.string.header_camera_unavailable
    is CameraStatus.Failed -> R.string.header_camera_failed
}

private fun CameraStatus.tone(): StatusTone = when (this) {
    CameraStatus.Ready -> StatusTone.SUCCESS
    CameraStatus.Starting -> StatusTone.MUTED
    CameraStatus.PermissionRequired, CameraStatus.NoCameraAvailable -> StatusTone.WARNING
    is CameraStatus.Failed -> StatusTone.DANGER
}

private fun CalibrationUiState.labelRes(): Int = when (this) {
    CalibrationUiState.NONE -> R.string.header_calibration_none
    CalibrationUiState.NEEDS_CHECK -> R.string.header_calibration_needs_check
    CalibrationUiState.VALID -> R.string.header_calibration_valid
}

private fun CalibrationUiState.tone(): StatusTone = when (this) {
    CalibrationUiState.NONE -> StatusTone.MUTED
    CalibrationUiState.NEEDS_CHECK -> StatusTone.WARNING
    CalibrationUiState.VALID -> StatusTone.SUCCESS
}

private fun LinkState.labelRes(): Int = when (this) {
    LinkState.DISCONNECTED -> R.string.header_usb_disconnected
    LinkState.CONNECTING -> R.string.header_usb_connecting
    LinkState.CONNECTED -> R.string.header_usb_connected
    LinkState.FAILED -> R.string.header_usb_failed
}

private fun LinkState.tone(): StatusTone = when (this) {
    LinkState.CONNECTED -> StatusTone.SUCCESS
    LinkState.CONNECTING -> StatusTone.MUTED
    LinkState.DISCONNECTED -> StatusTone.MUTED
    LinkState.FAILED -> StatusTone.DANGER
}

private fun RobotUiState.labelRes(): Int = when (this) {
    RobotUiState.INACTIVE -> R.string.robot_inactive
    RobotUiState.READY -> R.string.robot_state_ready
    RobotUiState.PREPARING -> R.string.robot_state_preparing
    RobotUiState.PICKING -> R.string.robot_state_picking
    RobotUiState.PLACING -> R.string.robot_state_placing
    RobotUiState.FAULT -> R.string.robot_state_fault
}

private fun RobotUiState.tone(): StatusTone = when (this) {
    RobotUiState.READY -> StatusTone.SUCCESS
    RobotUiState.PREPARING, RobotUiState.PICKING, RobotUiState.PLACING -> StatusTone.ACCENT
    RobotUiState.FAULT -> StatusTone.DANGER
    RobotUiState.INACTIVE -> StatusTone.MUTED
}

private fun StartBlocker.messageRes(): Int = when (this) {
    StartBlocker.CAMERA_NOT_READY -> R.string.guard_reason_camera
    StartBlocker.PROFILE_MISSING -> R.string.guard_reason_profile
    StartBlocker.NO_ACTIVE_CLASS -> R.string.guard_reason_no_active_class
    StartBlocker.CALIBRATION_MISSING -> R.string.guard_reason_calibration_missing
    StartBlocker.CALIBRATION_NEEDS_CHECK -> R.string.guard_reason_calibration_needs_check
    StartBlocker.CONTROLLER_DISCONNECTED -> R.string.guard_reason_controller
    StartBlocker.CONFIG_INCOMPLETE -> R.string.guard_reason_config_incomplete
    StartBlocker.BIN_MAPPING_INCOMPLETE -> R.string.guard_reason_bin_mapping
}
