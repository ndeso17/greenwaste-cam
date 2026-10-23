package id.pemilah.calibration

/**
 * Kalibrasi kamera sesuai `.work/Schema.md` §5 dan `.work/spec-machine.md` §4.
 *
 * Aturan yang dijaga model ini:
 * - `mappingMethod == null` berarti metode belum diputuskan (B-03), sehingga
 *   kalibrasi tidak sah meski titik acuan ada.
 * - Status `valid` tidak pernah dinaikkan otomatis; hanya operator yang boleh
 *   menetapkannya setelah pemeriksaan (CAL-06).
 * - Perubahan geometri memaksa `needs_check` (lihat [CalibrationInvalidation]).
 */
data class CameraCalibration(
    val schemaVersion: Int,
    val captureWidthPx: Int,
    val captureHeightPx: Int,
    val rotationDegrees: Int,
    val referencePoints: List<ReferencePoint> = emptyList(),
    val mappingMethod: MappingMethod? = null,
    val reprojectionError: Double? = null,
    val validity: CalibrationValidity = CalibrationValidity.needsCheck(ValidityReason.NOT_CHECKED),
) {
    val isUsable: Boolean
        get() = mappingMethod != null &&
            referencePoints.size >= MIN_REFERENCE_POINTS &&
            reprojectionError != null &&
            reprojectionError.isFinite() &&
            reprojectionError >= 0.0 &&
            validity.state == ValidityState.VALID

    companion object {
        const val SCHEMA_VERSION: Int = 1
        const val MIN_REFERENCE_POINTS: Int = 3
        val SUPPORTED_ROTATIONS: Set<Int> = setOf(0, 90, 180, 270)
    }
}

data class ReferencePoint(val imageX: Double, val imageY: Double, val worldX: Double, val worldY: Double)

/** Metode pemetaan gambar ke area kerja. Nilai metode menunggu B-03. */
enum class MappingMethod { HOMOGRAPHY, AFFINE }

enum class ValidityState { VALID, NEEDS_CHECK }

enum class ValidityReason {
    NOT_CHECKED,
    GEOMETRY_CHANGED,
    CAPTURE_RESOLUTION_CHANGED,
    ROTATION_CHANGED,
    METHOD_MISSING,
    INSUFFICIENT_POINTS,
    REPROJECTION_ERROR_MISSING,
    MANUAL_REVIEW,
}

data class CalibrationValidity(
    val state: ValidityState,
    val reason: ValidityReason?,
    val checkedAtEpochMs: Long? = null,
    val checkedNote: String? = null,
) {
    companion object {
        fun valid(checkedAtEpochMs: Long, note: String? = null): CalibrationValidity =
            CalibrationValidity(ValidityState.VALID, null, checkedAtEpochMs, note)

        fun needsCheck(reason: ValidityReason): CalibrationValidity =
            CalibrationValidity(ValidityState.NEEDS_CHECK, reason)
    }
}

sealed interface CalibrationCheckResult {
    data object Usable : CalibrationCheckResult
    data class Unusable(val reasons: List<ValidityReason>) : CalibrationCheckResult
}

object CameraCalibrationChecker {

    /** Alasan kalibrasi tidak dapat dipakai; kosong berarti dapat dipakai. */
    fun check(calibration: CameraCalibration): CalibrationCheckResult {
        val reasons = buildList {
            if (calibration.validity.state == ValidityState.NEEDS_CHECK) {
                add(calibration.validity.reason ?: ValidityReason.MANUAL_REVIEW)
            }
            if (calibration.mappingMethod == null) add(ValidityReason.METHOD_MISSING)
            if (calibration.referencePoints.size < CameraCalibration.MIN_REFERENCE_POINTS) {
                add(ValidityReason.INSUFFICIENT_POINTS)
            }
            val error = calibration.reprojectionError
            if (error == null || !error.isFinite() || error < 0.0) {
                add(ValidityReason.REPROJECTION_ERROR_MISSING)
            }
        }.distinct()
        return if (reasons.isEmpty()) CalibrationCheckResult.Usable else CalibrationCheckResult.Unusable(reasons)
    }
}
