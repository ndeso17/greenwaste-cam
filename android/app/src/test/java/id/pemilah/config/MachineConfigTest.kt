package id.pemilah.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture bernama; tidak ada angka mesin nyata (Styleguide.md §6). Semua nilai
 * di bawah hanya untuk menguji aturan validasi.
 */
private object Fixture {
    const val SCHEMA_VERSION = 1
    const val SURFACE_WIDTH_MM = 200.0
    const val TURN_RADIUS_MM = 300.0
    const val SEGMENT_MM = 500.0
    const val ENCODER_TICKS_PER_METER = 1200.0
    const val SPEED_MIN = 0.05
    const val SPEED_MAX = 0.15
    const val SEGMENT_COUNT = 3
    const val JOINT_COUNT = 3
    const val GRIPPER_MIN_MM = 10.0
    const val GRIPPER_MAX_MM = 80.0

    val detectionArea = listOf(
        PointMm(0.0, 0.0),
        PointMm(100.0, 0.0),
        PointMm(100.0, 100.0),
    )

    fun conveyor() = ConveyorConfig(
        surfaceWidthMm = SURFACE_WIDTH_MM,
        turnRadiusMm = TURN_RADIUS_MM,
        segmentLengthsMm = List(SEGMENT_COUNT) { SEGMENT_MM },
        direction = ConveyorDirection.CLOCKWISE,
        speedMinMetersPerSecond = SPEED_MIN,
        speedMaxMetersPerSecond = SPEED_MAX,
        encoderTicksPerMeter = ENCODER_TICKS_PER_METER,
        detectionArea = detectionArea,
        pickupArea = detectionArea,
    )

    fun robot(
        robotId: RobotId = RobotId.LEFT,
        enabled: Boolean = true,
        binIds: List<String> = robotId.binIds,
    ) = RobotConfig(
        robotId = robotId,
        enabled = enabled,
        basePosition = PointMm(0.0, 0.0),
        segmentLengthsMm = List(JOINT_COUNT) { SEGMENT_MM },
        mountHeightMm = 0.0,
        zeroAnglesDeg = List(JOINT_COUNT) { 0.0 },
        rotationDirections = List(JOINT_COUNT) { RotationDirection.POSITIVE },
        baseLimitsDeg = AngleRange(-90.0, 90.0),
        jointLimitsDeg = List(JOINT_COUNT) { AngleRange(-90.0, 90.0) },
        gripperOpeningMm = Range(GRIPPER_MIN_MM, GRIPPER_MAX_MM),
        maxTargetSizeMm = Range(GRIPPER_MIN_MM, GRIPPER_MAX_MM),
        pickupArea = detectionArea,
        binPositions = binIds.associateWith { PointMm(0.0, 0.0) },
    )

    fun config(
        conveyor: ConveyorConfig = conveyor(),
        robots: List<RobotConfig> = listOf(robot()),
    ) = MachineConfig(
        schemaVersion = SCHEMA_VERSION,
        conveyor = conveyor,
        robots = robots,
    )
}

class MachineConfigTest {

    private fun problemsOf(result: ValidationResult): List<ConfigProblem> {
        assertTrue("expected Invalid but was $result", result is ValidationResult.Invalid)
        return (result as ValidationResult.Invalid).problems
    }

    @Test
    fun validate_completeConfig_isValid() {
        assertEquals(ValidationResult.Valid, MachineConfigValidator.validate(Fixture.config()))
    }

    @Test
    fun validate_unsetValues_isValidBecauseNullMeansNotConfigured() {
        val empty = MachineConfig(
            schemaVersion = Fixture.SCHEMA_VERSION,
            conveyor = ConveyorConfig(),
            robots = listOf(RobotConfig(robotId = RobotId.LEFT)),
        )

        assertEquals(ValidationResult.Valid, MachineConfigValidator.validate(empty))
    }

    @Test
    fun validate_unknownSchemaVersion_isRejected() {
        val config = Fixture.config().copy(schemaVersion = Fixture.SCHEMA_VERSION + 1)

        val problems = problemsOf(MachineConfigValidator.validate(config))

        assertTrue(problems.any { it.reason == ConfigProblemReason.UNKNOWN_SCHEMA_VERSION })
    }

    @Test
    fun validate_encoderFactorZero_isRejected() {
        val config = Fixture.config(conveyor = Fixture.conveyor().copy(encoderTicksPerMeter = 0.0))

        val problems = problemsOf(MachineConfigValidator.validate(config))

        assertTrue(problems.any { it.reason == ConfigProblemReason.ENCODER_FACTOR_ZERO })
    }

    @Test
    fun validate_invertedSpeedRange_isRejected() {
        val conveyor = Fixture.conveyor().copy(
            speedMinMetersPerSecond = Fixture.SPEED_MAX,
            speedMaxMetersPerSecond = Fixture.SPEED_MIN,
        )

        val problems = problemsOf(MachineConfigValidator.validate(Fixture.config(conveyor = conveyor)))

        assertTrue(problems.any { it.reason == ConfigProblemReason.INVERTED_RANGE })
    }

    @Test
    fun validate_notFiniteSpeed_isRejected() {
        val conveyor = Fixture.conveyor().copy(speedMinMetersPerSecond = Double.NaN)

        val problems = problemsOf(MachineConfigValidator.validate(Fixture.config(conveyor = conveyor)))

        assertTrue(problems.any { it.reason == ConfigProblemReason.NOT_FINITE })
    }

    @Test
    fun validate_rightRobotOwningLeftBin_isRejected() {
        val right = Fixture.robot(robotId = RobotId.RIGHT, binIds = listOf("B1"))

        val problems = problemsOf(
            MachineConfigValidator.validate(Fixture.config(robots = listOf(right))),
        )

        assertTrue(problems.any { it.reason == ConfigProblemReason.BIN_NOT_OWNED_BY_ROBOT })
    }

    @Test
    fun validate_duplicateRobotId_isRejected() {
        val robots = listOf(Fixture.robot(), Fixture.robot())

        val problems = problemsOf(MachineConfigValidator.validate(Fixture.config(robots = robots)))

        assertTrue(problems.any { it.reason == ConfigProblemReason.DUPLICATE_ROBOT })
    }

    @Test
    fun validate_wrongJointCount_isRejected() {
        val robot = Fixture.robot().copy(segmentLengthsMm = listOf(Fixture.SEGMENT_MM))

        val problems = problemsOf(
            MachineConfigValidator.validate(Fixture.config(robots = listOf(robot))),
        )

        assertTrue(problems.any { it.reason == ConfigProblemReason.WRONG_SEGMENT_COUNT })
    }

    @Test
    fun validate_twoPointPolygon_isRejected() {
        val conveyor = Fixture.conveyor().copy(detectionArea = Fixture.detectionArea.take(2))

        val problems = problemsOf(MachineConfigValidator.validate(Fixture.config(conveyor = conveyor)))

        assertTrue(problems.any { it.reason == ConfigProblemReason.DEGENERATE_POLYGON })
    }

    @Test
    fun validate_invertedGripperRange_isRejected() {
        val robot = Fixture.robot().copy(
            gripperOpeningMm = Range(Fixture.GRIPPER_MAX_MM, Fixture.GRIPPER_MIN_MM),
        )

        val problems = problemsOf(
            MachineConfigValidator.validate(Fixture.config(robots = listOf(robot))),
        )

        assertTrue(problems.any { it.reason == ConfigProblemReason.INVERTED_RANGE })
    }

    @Test
    fun validateCompleteness_missingEncoder_isRejected() {
        val config = Fixture.config(conveyor = Fixture.conveyor().copy(encoderTicksPerMeter = null))

        val problems = problemsOf(MachineConfigValidator.validateCompleteness(config))

        assertTrue(problems.any { it.field == "conveyor.encoderTicksPerMeter" })
    }

    @Test
    fun validateCompleteness_noEnabledRobot_isRejected() {
        val robots = listOf(Fixture.robot(enabled = false))

        val problems = problemsOf(
            MachineConfigValidator.validateCompleteness(Fixture.config(robots = robots)),
        )

        assertTrue(problems.any { it.field == "robots" })
    }
}
