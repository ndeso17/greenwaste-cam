package id.pemilah.domain.operation

import id.pemilah.calibration.CalibrationValidity
import id.pemilah.calibration.CameraCalibration
import id.pemilah.calibration.MappingMethod
import id.pemilah.calibration.ReferencePoint
import id.pemilah.calibration.ValidityReason
import id.pemilah.config.ConveyorConfig
import id.pemilah.config.MachineConfig
import id.pemilah.config.RobotConfig
import id.pemilah.config.RobotId
import id.pemilah.domain.config.BinMapping
import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private object GuardFixture {
    const val SCHEMA_VERSION = 1
    const val CAPTURE_WIDTH_PX = 1280
    const val CAPTURE_HEIGHT_PX = 720
    const val ROTATION_DEGREES = 0
    const val CHECKED_AT_MS = 1_700_000_000_000L
    const val ENCODER_TICKS_PER_METER = 1200.0
    const val REPROJECTION_ERROR_PX = 1.0

    val referencePoints = listOf(
        ReferencePoint(0.0, 0.0, 0.0, 0.0),
        ReferencePoint(1.0, 0.0, 1.0, 0.0),
        ReferencePoint(0.0, 1.0, 0.0, 1.0),
    )

    fun calibration(validity: CalibrationValidity = CalibrationValidity.valid(CHECKED_AT_MS)) =
        CameraCalibration(
            schemaVersion = SCHEMA_VERSION,
            captureWidthPx = CAPTURE_WIDTH_PX,
            captureHeightPx = CAPTURE_HEIGHT_PX,
            rotationDegrees = ROTATION_DEGREES,
            referencePoints = referencePoints,
            mappingMethod = MappingMethod.HOMOGRAPHY,
            reprojectionError = REPROJECTION_ERROR_PX,
            validity = validity,
        )

    fun config(encoderTicks: Double? = ENCODER_TICKS_PER_METER) = MachineConfig(
        schemaVersion = SCHEMA_VERSION,
        conveyor = ConveyorConfig(
            encoderTicksPerMeter = encoderTicks,
            direction = id.pemilah.config.ConveyorDirection.CLOCKWISE,
        ),
        robots = listOf(RobotConfig(robotId = RobotId.LEFT, enabled = true)),
    )

    val bins = BinMapping(
        mapOf(
            WasteClass.ORGANIK to "B1",
            WasteClass.PLASTIK to "B2",
            WasteClass.KALENG to "B3",
        ),
        setOf(RobotId.LEFT),
    )

    fun readiness(
        cameraReady: Boolean = true,
        controllerConnected: Boolean = true,
        profileValid: Boolean = true,
        activeClasses: Set<WasteClass> = setOf(WasteClass.PLASTIK),
        calibration: CameraCalibration? = calibration(),
        config: MachineConfig = config(),
        binMapping: BinMapping = bins,
    ) = MachineReadiness(
        cameraReady = cameraReady,
        controllerConnected = controllerConnected,
        profileValid = profileValid,
        activeClasses = activeClasses,
        calibration = calibration,
        config = config,
        binMapping = binMapping,
    )
}

class OperationGuardTest {

    private fun codesOf(decision: StartDecision): List<BlockCode> {
        assertTrue("expected Blocked but was $decision", decision is StartDecision.Blocked)
        return (decision as StartDecision.Blocked).reasons.map { it.code }
    }

    @Test
    fun evaluate_completeReadiness_isAllowed() {
        assertEquals(StartDecision.Allowed, OperationGuard.evaluate(GuardFixture.readiness()))
    }

    @Test
    fun evaluate_cameraNotReady_blocks() {
        val codes = codesOf(OperationGuard.evaluate(GuardFixture.readiness(cameraReady = false)))

        assertEquals(listOf(BlockCode.CAMERA_NOT_READY), codes)
    }

    @Test
    fun evaluate_controllerDisconnected_blocks() {
        val codes = codesOf(
            OperationGuard.evaluate(GuardFixture.readiness(controllerConnected = false)),
        )

        assertTrue(BlockCode.CONTROLLER_DISCONNECTED in codes)
    }

    @Test
    fun evaluate_invalidProfile_blocks() {
        val codes = codesOf(OperationGuard.evaluate(GuardFixture.readiness(profileValid = false)))

        assertEquals(listOf(BlockCode.PROFILE_INVALID), codes)
    }

    @Test
    fun evaluate_noActiveClass_blocks() {
        val codes = codesOf(
            OperationGuard.evaluate(GuardFixture.readiness(activeClasses = emptySet())),
        )

        assertTrue(BlockCode.NO_ACTIVE_CLASS in codes)
    }

    @Test
    fun evaluate_missingCalibration_blocks() {
        val codes = codesOf(OperationGuard.evaluate(GuardFixture.readiness(calibration = null)))

        assertEquals(listOf(BlockCode.CALIBRATION_MISSING), codes)
    }

    @Test
    fun evaluate_calibrationNeedsCheck_blocksWithGeometryReason() {
        val calibration = GuardFixture.calibration(
            CalibrationValidity.needsCheck(ValidityReason.GEOMETRY_CHANGED),
        )

        val decision = OperationGuard.evaluate(GuardFixture.readiness(calibration = calibration))

        assertTrue(decision is StartDecision.Blocked)
        val reason = (decision as StartDecision.Blocked).reasons.single()
        assertEquals(BlockCode.CALIBRATION_NEEDS_CHECK, reason.code)
        assertEquals("GEOMETRY_CHANGED", reason.detail)
    }

    @Test
    fun evaluate_calibrationWithoutMethod_blocks() {
        val calibration = GuardFixture.calibration().copy(mappingMethod = null)

        val codes = codesOf(OperationGuard.evaluate(GuardFixture.readiness(calibration = calibration)))

        assertTrue(BlockCode.CALIBRATION_METHOD_MISSING in codes)
    }

    @Test
    fun evaluate_incompleteMachineConfig_blocks() {
        val codes = codesOf(
            OperationGuard.evaluate(
                GuardFixture.readiness(config = GuardFixture.config(encoderTicks = null)),
            ),
        )

        assertTrue(BlockCode.CONFIG_INCOMPLETE in codes)
    }

    @Test
    fun evaluate_activeClassWithoutBin_blocks() {
        val readiness = GuardFixture.readiness(
            activeClasses = setOf(WasteClass.PLASTIK, WasteClass.STYROFOAM),
        )

        val decision = OperationGuard.evaluate(readiness)

        assertTrue(decision is StartDecision.Blocked)
        val reason = (decision as StartDecision.Blocked).reasons
            .first { it.code == BlockCode.BIN_MAPPING_INCOMPLETE }
        assertEquals("STYROFOAM", reason.detail)
    }

    @Test
    fun evaluate_backendUnavailable_doesNotBlock() {
        // Tidak ada parameter backend pada guard; ketiadaan backend tidak boleh
        // mengubah hasil (Rules.md §2.8, COM-09).
        assertEquals(StartDecision.Allowed, OperationGuard.evaluate(GuardFixture.readiness()))
    }

    @Test
    fun transition_allowedStart_fromStopped_runs() {
        assertEquals(
            MachineState.Running,
            OperationGuard.transition(MachineState.Stopped, OperationIntent.START, StartDecision.Allowed),
        )
    }

    @Test
    fun transition_blockedStart_staysStopped() {
        val blocked = StartDecision.Blocked(listOf(BlockReason(BlockCode.CAMERA_NOT_READY)))

        assertEquals(
            MachineState.Stopped,
            OperationGuard.transition(MachineState.Stopped, OperationIntent.START, blocked),
        )
    }

    @Test
    fun transition_runningWithBlockedDecisionDoesNotAutoRestart() {
        assertEquals(
            MachineState.Running,
            OperationGuard.transition(
                MachineState.Running,
                OperationIntent.START,
                StartDecision.Blocked(listOf(BlockReason(BlockCode.CONTROLLER_DISCONNECTED))),
            ),
        )
    }

    @Test
    fun transition_stopFromRunning_stops() {
        assertEquals(
            MachineState.Stopped,
            OperationGuard.transition(MachineState.Running, OperationIntent.STOP, StartDecision.Allowed),
        )
    }
}
