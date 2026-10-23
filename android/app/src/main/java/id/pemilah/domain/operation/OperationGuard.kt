package id.pemilah.domain.operation

import id.pemilah.calibration.CalibrationCheckResult
import id.pemilah.calibration.CameraCalibration
import id.pemilah.calibration.CameraCalibrationChecker
import id.pemilah.calibration.ValidityReason
import id.pemilah.config.MachineConfig
import id.pemilah.config.MachineConfigValidator
import id.pemilah.config.ValidationResult
import id.pemilah.domain.config.BinMapping
import id.pemilah.domain.waste.WasteClass

/** Kondisi sistem yang diperiksa sebelum pemantauan boleh dimulai. */
data class MachineReadiness(
    val cameraReady: Boolean,
    val controllerConnected: Boolean,
    val profileValid: Boolean,
    val activeClasses: Set<WasteClass>,
    val calibration: CameraCalibration?,
    val config: MachineConfig,
    val binMapping: BinMapping,
)

/**
 * Satu alasan start ditolak, dengan kode stabil untuk UI dan parameter untuk
 * pesan yang dapat ditampilkan (Styleguide.md §5).
 */
data class BlockReason(
    val code: BlockCode,
    val detail: String? = null,
)

enum class BlockCode {
    CAMERA_NOT_READY,
    CONTROLLER_DISCONNECTED,
    PROFILE_INVALID,
    NO_ACTIVE_CLASS,
    CALIBRATION_MISSING,
    CALIBRATION_INSUFFICIENT_POINTS,
    CALIBRATION_METHOD_MISSING,
    CALIBRATION_REPROJECTION_MISSING,
    CALIBRATION_NEEDS_CHECK,
    CONFIG_INVALID,
    CONFIG_INCOMPLETE,
    BIN_MAPPING_INCOMPLETE,
}

/** Status operasi; hanya dua keadaan yang dapat bergerak ke [MachineState.Running]. */
enum class MachineState { Stopped, Running }

sealed interface StartDecision {
    data object Allowed : StartDecision
    data class Blocked(val reasons: List<BlockReason>) : StartDecision
}

/**
 * Syarat start pemantauan (WP-32). Backend sengaja **tidak** diperiksa:
 * backend tidak boleh menjadi syarat operasi (Rules.md §2.8).
 *
 * Guard ini tidak menggantikan emergency stop controller (Architecture.md §3).
 */
object OperationGuard {

    fun evaluate(readiness: MachineReadiness): StartDecision {
        val reasons = buildList {
            if (!readiness.cameraReady) add(BlockReason(BlockCode.CAMERA_NOT_READY))
            if (!readiness.controllerConnected) add(BlockReason(BlockCode.CONTROLLER_DISCONNECTED))
            if (!readiness.profileValid) add(BlockReason(BlockCode.PROFILE_INVALID))
            if (readiness.activeClasses.isEmpty()) add(BlockReason(BlockCode.NO_ACTIVE_CLASS))
            addAll(calibrationReasons(readiness.calibration))
            addAll(configReasons(readiness.config))
            addAll(binReasons(readiness))
        }
        return if (reasons.isEmpty()) StartDecision.Allowed else StartDecision.Blocked(reasons)
    }

    private fun calibrationReasons(calibration: CameraCalibration?): List<BlockReason> {
        if (calibration == null) return listOf(BlockReason(BlockCode.CALIBRATION_MISSING))
        val result = CameraCalibrationChecker.check(calibration)
        if (result == CalibrationCheckResult.Usable) return emptyList()
        return (result as CalibrationCheckResult.Unusable).reasons.map { it.toBlockReason() }
    }

    private fun ValidityReason.toBlockReason(): BlockReason = when (this) {
        ValidityReason.INSUFFICIENT_POINTS -> BlockReason(BlockCode.CALIBRATION_INSUFFICIENT_POINTS)
        ValidityReason.METHOD_MISSING -> BlockReason(BlockCode.CALIBRATION_METHOD_MISSING)
        ValidityReason.REPROJECTION_ERROR_MISSING ->
            BlockReason(BlockCode.CALIBRATION_REPROJECTION_MISSING)
        ValidityReason.NOT_CHECKED,
        ValidityReason.GEOMETRY_CHANGED,
        ValidityReason.CAPTURE_RESOLUTION_CHANGED,
        ValidityReason.ROTATION_CHANGED,
        ValidityReason.MANUAL_REVIEW,
        -> BlockReason(BlockCode.CALIBRATION_NEEDS_CHECK, name)
    }

    private fun configReasons(config: MachineConfig): List<BlockReason> = buildList {
        val validation = MachineConfigValidator.validate(config)
        if (validation is ValidationResult.Invalid) {
            add(
                BlockReason(
                    BlockCode.CONFIG_INVALID,
                    validation.problems.joinToString(",") { it.field },
                ),
            )
        }
        val completeness = MachineConfigValidator.validateCompleteness(config)
        if (completeness is ValidationResult.Invalid) {
            add(
                BlockReason(
                    BlockCode.CONFIG_INCOMPLETE,
                    completeness.problems.joinToString(",") { it.field },
                ),
            )
        }
    }

    private fun binReasons(readiness: MachineReadiness): List<BlockReason> {
        val blockers = readiness.binMapping.blockersFor(readiness.activeClasses)
        if (blockers.isEmpty()) return emptyList()
        return listOf(BlockReason(BlockCode.BIN_MAPPING_INCOMPLETE, blockers.joinToString(",")))
    }

    /**
     * Transisi state. Pemantauan hanya dimulai dari [MachineState.Stopped] dan
     * hanya melalui [StartDecision.Allowed]; tidak ada jalur yang memulai ulang
     * otomatis setelah gangguan atau putus koneksi (OperationScreen.xml rules).
     */
    fun transition(
        state: MachineState,
        intent: OperationIntent,
        decision: StartDecision,
    ): MachineState = when {
        intent == OperationIntent.STOP -> MachineState.Stopped
        state == MachineState.Running -> MachineState.Running
        decision == StartDecision.Allowed -> MachineState.Running
        else -> MachineState.Stopped
    }
}

enum class OperationIntent { START, STOP }
