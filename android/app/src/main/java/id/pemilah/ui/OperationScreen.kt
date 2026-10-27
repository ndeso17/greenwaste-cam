package id.pemilah.ui

import android.content.res.Configuration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.camera.CameraPreview
import id.pemilah.camera.CameraStatus
import id.pemilah.inference.ArgbFrame
import id.pemilah.inference.TileDetection
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
    classification: ClassificationUi = ClassificationUi.Waiting,
    detections: List<TileDetection> = emptyList(),
    detectionsAtMillis: Long = 0L,
    onStart: () -> Unit = {},
    onStop: () -> Unit = {},
    onFrame: ((ArgbFrame) -> Unit)? = null,
    onCameraStatusChange: (CameraStatus) -> Unit = {},
) {
    var panelVisible by rememberSaveable { mutableStateOf(false) }
    var panelScroll = rememberScrollState()
    var liveCameraStatus by remember { mutableStateOf<CameraStatus>(CameraStatus.Starting) }
    // Garis bantu bidik aktif secara bawaan: tanpa garis, operator sulit
    // menempatkan objek di tengah bidang pandang (instruksi pengguna 27 Okt 2026).
    var guidesVisible by rememberSaveable { mutableStateOf(true) }
    val ui = (state ?: rememberDefaultOperationState()).copy(cameraStatus = liveCameraStatus)
    val blockers = StartGuardProjection.blockers(ui)
    // Frame hanya diminta saat pemantauan berjalan; di luar itu tidak ada
    // inferensi diam-diam (D-13).
    val frameCallback: ((ArgbFrame) -> Unit)? = if (ui.isRunning) onFrame else null
    // Penanda usang butuh jam berjalan; hanya berdetak selama pemantauan aktif
    // supaya tidak ada pembaruan state yang tidak perlu (D-14).
    var nowMillis by remember { mutableStateOf(0L) }
    LaunchedEffect(ui.isRunning) {
        while (ui.isRunning) {
            nowMillis = System.currentTimeMillis()
            delay(500)
        }
    }
    val detectionsStale = ui.isRunning && detections.isNotEmpty() &&
        nowMillis - detectionsAtMillis > OperationSession.DETECTION_STALE_MS
    val detectionUi = if (ui.isRunning) {
        detections.map { detection ->
            DetectionUi(
                box = NormalizedBox(
                    left = detection.region.left,
                    top = detection.region.top,
                    right = detection.region.right,
                    bottom = detection.region.bottom,
                ),
                classLabel = detection.label,
                confidence = detection.probability,
                stale = detectionsStale,
            )
        }
    } else {
        emptyList()
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        val sidebarWidth = if (maxWidth < 600.dp) {
            (maxWidth - 24.dp).coerceAtMost(300.dp)
        } else {
            (maxWidth * 0.36f).coerceAtMost(340.dp)
        }
        CameraStage(
            state = ui,
            onStart = onStart,
            onStop = onStop,
            cameraStatus = liveCameraStatus,
            onCameraStatusChange = { status ->
                liveCameraStatus = status
                onCameraStatusChange(status)
            },
            guidesVisible = guidesVisible,
            onFrame = frameCallback,
            detections = detectionUi,
            modifier = Modifier.fillMaxSize(),
        )
        if (panelVisible) {
            Column(
                modifier = Modifier.align(Alignment.TopEnd)
                    .padding(top = 64.dp, end = 8.dp, bottom = 224.dp)
                    .width(sidebarWidth).fillMaxHeight()
                    .background(Palette.surface.copy(alpha = 0.94f), RoundedCornerShape(16.dp))
                    .verticalScroll(panelScroll).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(PanelGap),
            ) {
                if (blockers.isNotEmpty() || ui.isRunning) {
                    GuardBanner(blockers = blockers, isRunning = ui.isRunning, stage = ui.stage)
                }
                GuidesToggle(visible = guidesVisible, onVisibleChange = { guidesVisible = it })
                ClassificationPanel(state = ui, classification = classification)
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
            label = stringResource(HeaderField.PROFILE.labelRes),
            value = state.profileNameAndVersion
                ?: stringResource(R.string.header_profile_none),
            tone = if (state.profileNameAndVersion == null) {
                StatusTone.MUTED
            } else {
                StatusTone.SUCCESS
            },
        )
        StatusInfoDivider()
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
        Box(Modifier.size(8.dp).background(tone.color(), CircleShape))
        Text(label, color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun CameraStage(
    state: OperationUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    cameraStatus: CameraStatus,
    onCameraStatusChange: (CameraStatus) -> Unit,
    guidesVisible: Boolean,
    onFrame: ((ArgbFrame) -> Unit)?,
    detections: List<DetectionUi>,
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
            onFrame = onFrame,
        )
        // Garis bantu bidik: panduan visual, bukan area kerja terkalibrasi.
        CameraGuidesOverlay(visible = guidesVisible)
        // Overlay hanya menggambar data yang benar-benar ada; saat ini belum ada
        // geometri terkalibrasi sehingga tidak ada kotak atau blok yang dikarang.
        DetectionOverlay(
            workArea = null,
            blocks = emptyList(),
            detections = detections,
            target = null,
            targetLabel = stringResource(R.string.target_label),
            // Ringkasan aksesibilitas: kotak di kanvas tidak terbaca TalkBack,
            // jadi isi kotak dinyatakan sebagai teks (Design.md §7).
            modifier = Modifier.statusDescription(
                detectionsSummary(detections, stale = detections.any { it.stale }),
            ),
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
            SessionSummaryBar(state = state)
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

/**
 * Sakelar garis bantu bidik. Ditempatkan di panel agar tidak menutupi pratinjau,
 * dan menyebut dengan jelas bahwa garis ini bukan area kerja terkalibrasi
 * (instruksi pengguna 27 Oktober 2026).
 */
@Composable
private fun GuidesToggle(visible: Boolean, onVisibleChange: (Boolean) -> Unit) {
    MachinePanel(title = stringResource(R.string.guides_title)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.guides_toggle),
                    color = Palette.onSurface,
                    fontSize = 13.sp,
                )
                Text(
                    text = stringResource(R.string.guides_note),
                    color = Palette.onSurfaceMuted,
                    fontSize = 11.sp,
                )
            }
            Switch(checked = visible, onCheckedChange = onVisibleChange)
        }
    }
}

/**
 * Ringkasan kotak overlay untuk pembaca layar dan untuk pemeriksaan otomatis.
 * Tidak ada kotak berarti dinyatakan kosong, bukan disembunyikan.
 */
private fun detectionsSummary(detections: List<DetectionUi>, stale: Boolean): String {
    if (detections.isEmpty()) return "Tidak ada kotak objek"
    val parts = detections.joinToString(", ") { detection ->
        "${detection.classLabel} ${(detection.confidence * 100).toInt()}%"
    }
    return buildString {
        append(detections.size)
        append(" kotak objek: ")
        append(parts)
        if (stale) append(" (usang)")
    }
}

@Composable
private fun CameraStatusOverlay(    status: CameraStatus,
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
private fun SessionSummaryBar(state: OperationUiState) {
    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val session = state.session
    val metrics: @Composable () -> Unit = {
            SessionMetric(
                label = stringResource(R.string.session_motion_completed),
                fullLabel = stringResource(R.string.session_motion_completed_full),
                value = session?.motionCompleted,
                vertical = portrait,
                tone = StatusTone.MUTED,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_verified),
                fullLabel = stringResource(R.string.session_pickup_verified_full),
                value = session?.pickupVerified,
                vertical = portrait,
                tone = StatusTone.SUCCESS,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_unverified),
                fullLabel = stringResource(R.string.session_pickup_unverified_full),
                value = session?.pickupUnverified,
                vertical = portrait,
                tone = StatusTone.WARNING,
            )
            SessionMetric(
                label = stringResource(R.string.session_pickup_failed),
                fullLabel = stringResource(R.string.session_pickup_failed_full),
                value = session?.pickupFailed,
                vertical = portrait,
                tone = StatusTone.DANGER,
            )
            SessionMetric(
                label = stringResource(R.string.session_queue),
                fullLabel = stringResource(R.string.session_queue_full),
                value = state.pendingEvents,
                vertical = portrait,
                tone = if (state.pendingEvents != null && state.pendingEvents > 0) {
                    StatusTone.WARNING
                } else {
                    StatusTone.MUTED
                },
            )
    }
    if (portrait) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.panel_session), color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            metrics()
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.panel_session), color = Color.White, fontSize = 12.sp, lineHeight = 16.sp)
            metrics()
        }
    }
}

@Composable
private fun SessionMetric(
    label: String,
    fullLabel: String,
    value: Int?,
    tone: StatusTone,
    vertical: Boolean,
) {
    // Singkatan tampil di layar; `fullLabel` menjadi deskripsi pembaca layar agar
    // makna status tidak hilang (Design.md §7: status wajib punya teks bermakna).
    Row(
        modifier = (if (vertical) Modifier.fillMaxWidth() else Modifier).statusDescription("$fullLabel: ${value ?: "-"}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = if (vertical) Modifier.weight(1f) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusIcon(tone)
            Text(
                text = if (vertical) fullLabel else label,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (vertical) Int.MAX_VALUE else 1,
            )
        }
        Text(
            text = value?.toString() ?: stringResource(R.string.session_value_none),
            color = Color.White,
            fontSize = 12.sp,
                lineHeight = 16.sp,
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
    CameraStatus.PermissionRequired -> StatusTone.MUTED
    CameraStatus.NoCameraAvailable -> StatusTone.DANGER
    is CameraStatus.Failed -> StatusTone.DANGER
}

private fun CalibrationUiState.labelRes(): Int = when (this) {
    CalibrationUiState.NONE -> R.string.header_calibration_none
    CalibrationUiState.NEEDS_CHECK -> R.string.header_calibration_needs_check
    CalibrationUiState.VALID -> R.string.header_calibration_valid
}

private fun CalibrationUiState.tone(): StatusTone = when (this) {
    CalibrationUiState.NONE -> StatusTone.MUTED
    CalibrationUiState.NEEDS_CHECK -> StatusTone.DANGER
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
