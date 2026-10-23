package id.pemilah.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private object Fixture {
    const val SCHEMA_VERSION = 1
    const val CAPTURE_WIDTH_PX = 1280
    const val CAPTURE_HEIGHT_PX = 720
    const val ROTATION_DEGREES = 0
    const val CHECKED_AT_MS = 1_700_000_000_000L
    const val REPROJECTION_ERROR_PX = 1.5

    val referencePoints = listOf(
        ReferencePoint(0.0, 0.0, 0.0, 0.0),
        ReferencePoint(100.0, 0.0, 50.0, 0.0),
        ReferencePoint(0.0, 100.0, 0.0, 50.0),
    )

    fun checked(
        method: MappingMethod? = MappingMethod.HOMOGRAPHY,
        points: List<ReferencePoint> = referencePoints,
        error: Double? = REPROJECTION_ERROR_PX,
    ) = CameraCalibration(
        schemaVersion = SCHEMA_VERSION,
        captureWidthPx = CAPTURE_WIDTH_PX,
        captureHeightPx = CAPTURE_HEIGHT_PX,
        rotationDegrees = ROTATION_DEGREES,
        referencePoints = points,
        mappingMethod = method,
        reprojectionError = error,
        validity = CalibrationValidity.valid(CHECKED_AT_MS),
    )
}

class CameraCalibrationTest {

    private fun reasonsOf(result: CalibrationCheckResult): List<ValidityReason> {
        assertTrue("expected Unusable but was $result", result is CalibrationCheckResult.Unusable)
        return (result as CalibrationCheckResult.Unusable).reasons
    }

    @Test
    fun check_checkedCalibration_isUsable() {
        assertEquals(CalibrationCheckResult.Usable, CameraCalibrationChecker.check(Fixture.checked()))
    }

    @Test
    fun isUsable_mappingMethodMissing_isFalse() {
        val calibration = Fixture.checked(method = null)

        assertFalse(calibration.isUsable)
    }

    @Test
    fun check_mappingMethodMissing_reportsMethodMissing() {
        val reasons = reasonsOf(CameraCalibrationChecker.check(Fixture.checked(method = null)))

        assertTrue(ValidityReason.METHOD_MISSING in reasons)
    }

    @Test
    fun check_tooFewReferencePoints_reportsInsufficientPoints() {
        val points = Fixture.referencePoints.take(CameraCalibration.MIN_REFERENCE_POINTS - 1)

        val reasons = reasonsOf(CameraCalibrationChecker.check(Fixture.checked(points = points)))

        assertTrue(ValidityReason.INSUFFICIENT_POINTS in reasons)
    }

    @Test
    fun check_missingReprojectionError_reportsErrorMissing() {
        val reasons = reasonsOf(CameraCalibrationChecker.check(Fixture.checked(error = null)))

        assertTrue(ValidityReason.REPROJECTION_ERROR_MISSING in reasons)
    }

    @Test
    fun check_negativeReprojectionError_reportsErrorMissing() {
        val reasons = reasonsOf(CameraCalibrationChecker.check(Fixture.checked(error = -1.0)))

        assertTrue(ValidityReason.REPROJECTION_ERROR_MISSING in reasons)
    }

    @Test
    fun check_needsCheckValidity_isNotUsable() {
        val calibration = Fixture.checked().copy(
            validity = CalibrationValidity.needsCheck(ValidityReason.GEOMETRY_CHANGED),
        )

        val reasons = reasonsOf(CameraCalibrationChecker.check(calibration))

        assertTrue(ValidityReason.GEOMETRY_CHANGED in reasons)
    }

    @Test
    fun check_needsCheckWithoutReason_reportsManualReview() {
        val calibration = Fixture.checked().copy(
            validity = CalibrationValidity(ValidityState.NEEDS_CHECK, reason = null),
        )

        val reasons = reasonsOf(CameraCalibrationChecker.check(calibration))

        assertTrue(ValidityReason.MANUAL_REVIEW in reasons)
    }

    @Test
    fun isUsable_needsCheckState_isFalseEvenWithCompleteData() {
        val calibration = Fixture.checked().copy(
            validity = CalibrationValidity.needsCheck(ValidityReason.GEOMETRY_CHANGED),
        )

        assertFalse(calibration.isUsable)
    }
}
