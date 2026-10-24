package id.pemilah.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import id.pemilah.usb.LinkState

/**
 * Layar operasi sesuai `.work/OperationScreen.xml` dan `Design.md` §4:
 * header status → pratinjau kamera dominan + ringkasan sesi → panel mesin →
 * banner guard → kontrol operasi persisten di bawah.
 *
 * Aturan yang dijaga:
 * - Tahap 3 hanya memantau. Panel robot, tujuan bak, dan kecepatan menampilkan
 *   "Belum terintegrasi" beserta alasannya, bukan nilai atau status siap palsu.
 * - Tombol Mulai nonaktif selama guard gagal; alasannya tampil tepat di atas
 *   kontrol (Design.md §4.2).
 * - Tidak ada angka contoh: metrik sesi kosong ditulis "—", kecepatan aktual
 *   kosong ditulis "Belum ada data".
 */
@Composable
fun OperationScreen(
    modifier: Modifier = Modifier,
    state: OperationUiState? = null,
    onOpenModels: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
) {
    val ui = state ?: rememberDefaultOperationState()
    val blockers = StartGuardProjection.blockers(ui)
    Column(modifier = modifier.fillMaxSize()) {
        StatusHeader(state = ui, onOpenModels = onOpenModels)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
                    .padding(start = 16.dp, end = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                CameraStage(
                    state = ui,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                SessionSummaryBar(
                    state = ui,
                    onOpenHistory = onOpenHistory,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                TargetPanel(state = ui)
                RobotPanel(state = ui)
                ConveyorPanel(state = ui)
            }
        }
        if (blockers.isNotEmpty() || ui.isRunning) {
            GuardBanner(
                blockers = blockers,
                isRunning = ui.isRunning,
                stage = ui.stage,
            )
        }
        OperationControls(
            state = ui,
            blockers = blockers,
            onStart = onStart,
            onStop = onStop,
        )
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
private fun StatusHeader(state: OperationUiState, onOpenModels: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_title),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(
            onClick = onOpenModels,
            modifier = Modifier.statusDescription(stringResource(R.string.header_open_models)),
        ) {
            Text(
                text = state.profileNameAndVersion ?: stringResource(R.string.header_profile_none),
                color = if (state.profileNameAndVersion == null) {
                    Palette.onSurfaceMuted
                } else {
                    Palette.accent
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        StatusChip(
            label = stringResource(R.string.header_camera),
            value = stringResource(state.cameraStatus.labelRes()),
            tone = state.cameraStatus.tone(),
        )
        StatusChip(
            label = stringResource(R.string.header_calibration),
            value = stringResource(state.calibration.labelRes()),
            tone = state.calibration.tone(),
        )
        StatusChip(
            label = stringResource(R.string.header_usb),
            value = stringResource(state.linkState.labelRes()),
            tone = state.linkState.tone(),
        )
    }
}

@Composable
private fun CameraStage(state: OperationUiState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Palette.surfaceRaised, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.onSurfaceMuted.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
    ) {
        val context = LocalContext.current
        var permissionGranted by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED,
            )
        }
        var cameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted -> permissionGranted = granted }

        CameraPreview(
            hasPermission = permissionGranted,
            modifier = Modifier.fillMaxSize(),
            onStatusChange = { cameraStatus = it },
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
        if (state.isRunning) {
            Text(
                text = stringResource(R.string.operation_status_monitoring),
                color = Palette.success,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(Palette.surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
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
    MachinePanel(
        title = stringResource(R.string.panel_session),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val session = state.session
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            SessionMetric(
                label = stringResource(R.string.session_motion_completed),
                value = session?.motionCompleted,
                tone = StatusTone.MUTED,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_verified),
                value = session?.pickupVerified,
                tone = StatusTone.SUCCESS,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_unverified),
                value = session?.pickupUnverified,
                tone = StatusTone.WARNING,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_failed),
                value = session?.pickupFailed,
                tone = StatusTone.DANGER,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.session_queue),
                    color = Palette.onSurfaceMuted,
                    fontSize = 12.sp,
                )
                Text(
                    text = state.pendingEvents?.toString()
                        ?: stringResource(R.string.session_value_none),
                    color = Palette.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            StatusChip(
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
            SecondaryAction(
                label = stringResource(R.string.session_open_history),
                onClick = onOpenHistory,
            )
        }
        if (session == null) {
            PlaceholderText(text = stringResource(R.string.session_empty))
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: Int?, tone: StatusTone) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusIcon(tone)
            Text(text = label, color = Palette.onSurfaceMuted, fontSize = 12.sp)
        }
        Text(
            text = value?.toString() ?: stringResource(R.string.session_value_none),
            color = if (value == null) Palette.onSurfaceMuted else Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
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
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(Palette.surfaceRaised, RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (isRunning) Palette.success.copy(alpha = 0.4f) else Palette.danger.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(10.dp),
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
                color = Palette.success,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            return@Column
        }
        Text(
            text = stringResource(R.string.guard_banner_title),
            color = Palette.danger,
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

@Composable
private fun OperationControls(
    state: OperationUiState,
    blockers: List<StartBlocker>,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val sorting = state.stage == OperationStage.SORTING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.surfaceRaised)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PrimaryAction(
            label = stringResource(
                if (sorting) R.string.operation_start_sorting else R.string.operation_start_monitoring,
            ),
            onClick = onStart,
            enabled = StartGuardProjection.canStart(state),
        )
        PrimaryAction(
            label = stringResource(
                if (sorting) R.string.operation_stop_sorting else R.string.operation_stop_monitoring,
            ),
            onClick = onStop,
            enabled = StartGuardProjection.canStop(state),
            tone = StatusTone.DANGER,
        )
        Text(
            text = stringResource(
                if (state.isRunning) R.string.operation_status_running else R.string.operation_status_stopped,
            ),
            color = if (state.isRunning) Palette.success else Palette.onSurfaceMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (blockers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.operation_stop_hint),
                color = Palette.onSurfaceMuted,
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
