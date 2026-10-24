package id.pemilah.ui

import id.pemilah.camera.CameraStatus
import id.pemilah.usb.LinkState

/**
 * Tahap integrasi yang sedang ditampilkan (PRD tahap 3 bagian 2–4).
 *
 * Tahap 3 hanya memantau: tidak mengirim perintah aktuator, tidak memerlukan
 * USB/robot. Tahap 4 dan seterusnya baru menampilkan kendali mesin.
 */
enum class OperationStage { MONITORING, SORTING }

/** Status kalibrasi kamera sebagaimana terlihat UI; `null` berarti belum ada. */
enum class CalibrationUiState { NONE, NEEDS_CHECK, VALID }

/** Status backend; tidak pernah menjadi syarat operasi (COM-09). */
enum class BackendUiState { UNKNOWN, CONNECTED, UNAVAILABLE }

/** Status satu robot menurut telemetri; `null` berarti belum ada data. */
enum class RobotUiState { INACTIVE, READY, PREPARING, PICKING, PLACING, FAULT }

/** Target aktif menurut deteksi/pelacakan; `null` berarti belum ada target. */
data class ActiveTargetUi(
    val id: String,
    val classLabel: String,
    val blockAndPosition: String?,
    val binAndRobot: String?,
)

/** Ringkasan sesi; `null` berarti belum ada siklus, bukan nol (Design.md §4.4). */
data class SessionSummaryUi(
    val motionCompleted: Int?,
    val pickupVerified: Int?,
    val pickupUnverified: Int?,
    val pickupFailed: Int?,
)

/** Kecepatan conveyor; `requested` adalah draft operator, `actual` dari encoder. */
data class ConveyorUi(
    val requestedDraft: String?,
    val actualEncoder: String?,
    val actualStale: Boolean,
    val speedUnit: String?,
)

/**
 * Seluruh data yang dibutuhkan layar operasi (`.work/OperationScreen.xml`).
 *
 * Semua kolom bertipe nullable dan default `null`/`UNKNOWN`: tidak ada angka atau
 * status contoh yang lahir dari model tampilan ini. Nilai hanya diisi oleh
 * pemilik state sebenarnya (kamera, kalibrasi, `ControllerConnection`,
 * deteksi/target, sesi, backend).
 */
data class OperationUiState(
    val stage: OperationStage = OperationStage.MONITORING,
    val cameraStatus: CameraStatus = CameraStatus.Starting,
    val hasCameraPermission: Boolean = false,
    val profileNameAndVersion: String? = null,
    val calibration: CalibrationUiState = CalibrationUiState.NONE,
    val linkState: LinkState = LinkState.DISCONNECTED,
    val isRunning: Boolean = false,
    val activeTarget: ActiveTargetUi? = null,
    val session: SessionSummaryUi? = null,
    val backend: BackendUiState = BackendUiState.UNKNOWN,
    val pendingEvents: Int? = null,
    val leftRobot: RobotUiState? = null,
    val rightRobot: RobotUiState? = null,
    val conveyor: ConveyorUi = ConveyorUi(null, null, false, null),
    val activeClassesConfigured: Boolean = false,
    val machineConfigComplete: Boolean = false,
    val binMappingComplete: Boolean = false,
)

/**
 * Alasan tombol Mulai nonaktif, diproyeksikan dari [OperationUiState].
 *
 * Urutan dan cakupan mengikuti guard per mode pada `.work/OperationScreen.xml`
 * (`modes`): pemantauan memeriksa kamera, profil, kalibrasi, dan gangguan;
 * pemilahan menambah koneksi, konfigurasi mesin, pemetaan kelas, dan kalibrasi
 * terkait. Backend **tidak** pernah menjadi alasan (COM-09).
 */
enum class StartBlocker {
    CAMERA_NOT_READY,
    PROFILE_MISSING,
    NO_ACTIVE_CLASS,
    CALIBRATION_MISSING,
    CALIBRATION_NEEDS_CHECK,
    CONTROLLER_DISCONNECTED,
    CONFIG_INCOMPLETE,
    BIN_MAPPING_INCOMPLETE,
}

object StartGuardProjection {

    fun blockers(state: OperationUiState): List<StartBlocker> = buildList {
        if (state.cameraStatus != CameraStatus.Ready) add(StartBlocker.CAMERA_NOT_READY)
        if (state.profileNameAndVersion == null) add(StartBlocker.PROFILE_MISSING)
        if (!state.activeClassesConfigured) add(StartBlocker.NO_ACTIVE_CLASS)
        when (state.calibration) {
            CalibrationUiState.NONE -> add(StartBlocker.CALIBRATION_MISSING)
            CalibrationUiState.NEEDS_CHECK -> add(StartBlocker.CALIBRATION_NEEDS_CHECK)
            CalibrationUiState.VALID -> Unit
        }
        if (state.stage == OperationStage.SORTING) {
            if (state.linkState != LinkState.CONNECTED) add(StartBlocker.CONTROLLER_DISCONNECTED)
            if (!state.machineConfigComplete) add(StartBlocker.CONFIG_INCOMPLETE)
            if (!state.binMappingComplete) add(StartBlocker.BIN_MAPPING_INCOMPLETE)
        }
    }

    /** Mulai hanya aktif bila guard lolos dan mode belum berjalan. */
    fun canStart(state: OperationUiState): Boolean =
        !state.isRunning && blockers(state).isEmpty()

    fun canStop(state: OperationUiState): Boolean = state.isRunning
}
