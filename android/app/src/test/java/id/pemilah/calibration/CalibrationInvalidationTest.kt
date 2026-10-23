package id.pemilah.calibration

import id.pemilah.config.ConveyorConfig
import id.pemilah.config.MachineConfig
import id.pemilah.config.PointMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

private object InvalidationFixture {
    const val SCHEMA_VERSION = 1
    const val CAPTURE_WIDTH_PX = 1280
    const val CAPTURE_HEIGHT_PX = 720
    const val ROTATION_DEGREES = 0
    const val SURFACE_WIDTH_MM = 200.0
    const val SEGMENT_MM = 500.0
    const val CHECKED_AT_MS = 1_700_000_000_000L

    val area = listOf(PointMm(0.0, 0.0), PointMm(10.0, 0.0), PointMm(10.0, 10.0))

    fun calibration(
        width: Int = CAPTURE_WIDTH_PX,
        height: Int = CAPTURE_HEIGHT_PX,
        rotation: Int = ROTATION_DEGREES,
    ) = CameraCalibration(
        schemaVersion = SCHEMA_VERSION,
        captureWidthPx = width,
        captureHeightPx = height,
        rotationDegrees = rotation,
        referencePoints = listOf(
            ReferencePoint(0.0, 0.0, 0.0, 0.0),
            ReferencePoint(1.0, 0.0, 1.0, 0.0),
            ReferencePoint(0.0, 1.0, 0.0, 1.0),
        ),
        mappingMethod = MappingMethod.HOMOGRAPHY,
        reprojectionError = 1.0,
        validity = CalibrationValidity.valid(CHECKED_AT_MS),
    )

    fun config(
        surfaceWidth: Double = SURFACE_WIDTH_MM,
        detectionArea: List<PointMm> = area,
    ) = MachineConfig(
        schemaVersion = SCHEMA_VERSION,
        conveyor = ConveyorConfig(
            surfaceWidthMm = surfaceWidth,
            segmentLengthsMm = listOf(SEGMENT_MM),
            detectionArea = detectionArea,
        ),
        robots = emptyList(),
    )

    fun snapshot(
        width: Int = CAPTURE_WIDTH_PX,
        rotation: Int = ROTATION_DEGREES,
        surfaceWidth: Double = SURFACE_WIDTH_MM,
        detectionArea: List<PointMm> = area,
    ) = CalibrationInvalidation.GeometrySnapshot.of(
        calibration(width = width, rotation = rotation),
        config(surfaceWidth = surfaceWidth, detectionArea = detectionArea),
    )
}

class CalibrationInvalidationTest {

    @Test
    fun reasonFor_identicalGeometry_isNull() {
        assertNull(CalibrationInvalidation.reasonFor(InvalidationFixture.snapshot(), InvalidationFixture.snapshot()))
    }

    @Test
    fun apply_identicalGeometry_keepsValidStatus() {
        val calibration = InvalidationFixture.calibration()

        val result = CalibrationInvalidation.apply(
            calibration,
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(),
        )

        assertSame(calibration, result)
        assertEquals(ValidityState.VALID, result.validity.state)
    }

    @Test
    fun apply_resolutionChanged_marksNeedsCheck() {
        val result = CalibrationInvalidation.apply(
            InvalidationFixture.calibration(),
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(width = InvalidationFixture.CAPTURE_WIDTH_PX * 2),
        )

        assertEquals(ValidityState.NEEDS_CHECK, result.validity.state)
        assertEquals(ValidityReason.CAPTURE_RESOLUTION_CHANGED, result.validity.reason)
    }

    @Test
    fun apply_rotationChanged_marksNeedsCheck() {
        val result = CalibrationInvalidation.apply(
            InvalidationFixture.calibration(),
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(rotation = 90),
        )

        assertEquals(ValidityReason.ROTATION_CHANGED, result.validity.reason)
    }

    @Test
    fun apply_conveyorWidthChanged_marksGeometryChanged() {
        val result = CalibrationInvalidation.apply(
            InvalidationFixture.calibration(),
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(surfaceWidth = InvalidationFixture.SURFACE_WIDTH_MM + 1.0),
        )

        assertEquals(ValidityReason.GEOMETRY_CHANGED, result.validity.reason)
    }

    @Test
    fun apply_blockPolygonChanged_marksGeometryChanged() {
        val movedArea = listOf(
            PointMm(0.0, 0.0),
            PointMm(20.0, 0.0),
            PointMm(20.0, 20.0),
        )

        val result = CalibrationInvalidation.apply(
            InvalidationFixture.calibration(),
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(detectionArea = movedArea),
        )

        assertEquals(ValidityReason.GEOMETRY_CHANGED, result.validity.reason)
    }

    @Test
    fun apply_afterInvalidation_checkerRejectsCalibration() {
        val invalidated = CalibrationInvalidation.apply(
            InvalidationFixture.calibration(),
            InvalidationFixture.snapshot(),
            InvalidationFixture.snapshot(rotation = 180),
        )

        val result = CameraCalibrationChecker.check(invalidated)

        assertEquals(
            CalibrationCheckResult.Unusable(listOf(ValidityReason.ROTATION_CHANGED)),
            result,
        )
    }
}
