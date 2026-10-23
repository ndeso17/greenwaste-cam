package id.pemilah.config

/**
 * Konfigurasi mesin sesuai `.work/spec-machine.md` §2–§3 dan `.work/Schema.md` §4.
 *
 * Semua nilai numerik `null` berarti belum dikonfigurasi, bukan nol. Tidak ada
 * nilai bawaan: model ini tidak boleh mengisi angka mesin (Rules.md §2.2).
 */
data class MachineConfig(
    val schemaVersion: Int,
    val conveyor: ConveyorConfig,
    val robots: List<RobotConfig>,
)

data class ConveyorConfig(
    val surfaceWidthMm: Double? = null,
    val turnRadiusMm: Double? = null,
    val segmentLengthsMm: List<Double> = emptyList(),
    val direction: ConveyorDirection? = null,
    val speedMinMetersPerSecond: Double? = null,
    val speedMaxMetersPerSecond: Double? = null,
    val encoderTicksPerMeter: Double? = null,
    val detectionArea: List<PointMm> = emptyList(),
    val pickupArea: List<PointMm> = emptyList(),
)

enum class ConveyorDirection { CLOCKWISE, COUNTERCLOCKWISE }

data class PointMm(val x: Double, val y: Double)

data class RobotConfig(
    val robotId: RobotId,
    val enabled: Boolean = false,
    val basePosition: PointMm? = null,
    val segmentLengthsMm: List<Double> = emptyList(),
    val mountHeightMm: Double? = null,
    val zeroAnglesDeg: List<Double> = emptyList(),
    val rotationDirections: List<RotationDirection> = emptyList(),
    val baseLimitsDeg: AngleRange? = null,
    val jointLimitsDeg: List<AngleRange> = emptyList(),
    val gripperOpeningMm: Range? = null,
    val maxTargetSizeMm: Range? = null,
    val pickupArea: List<PointMm> = emptyList(),
    val binPositions: Map<String, PointMm> = emptyMap(),
    val motionParameters: Map<String, Double> = emptyMap(),
)

enum class RobotId(val binIds: List<String>) {
    LEFT(listOf("B1", "B2", "B3")),
    RIGHT(listOf("B4", "B5", "B6")),
}

enum class RotationDirection { POSITIVE, NEGATIVE }

data class AngleRange(val minDeg: Double, val maxDeg: Double)

data class Range(val min: Double, val max: Double)

/** Masalah konfigurasi dengan alasan spesifik yang dapat ditampilkan ke operator. */
data class ConfigProblem(val field: String, val reason: ConfigProblemReason)

enum class ConfigProblemReason {
    NOT_FINITE,
    NEGATIVE_OR_ZERO,
    NEGATIVE,
    INVERTED_RANGE,
    ENCODER_FACTOR_ZERO,
    BIN_NOT_OWNED_BY_ROBOT,
    DUPLICATE_ROBOT,
    WRONG_SEGMENT_COUNT,
    WRONG_JOINT_COUNT,
    DEGENERATE_POLYGON,
    UNKNOWN_SCHEMA_VERSION,
    NOT_CONFIGURED,
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val problems: List<ConfigProblem>) : ValidationResult
}

/**
 * Validasi konfigurasi mesin (CFG-01, CFG-02, ARM-04, ARM-06).
 *
 * Hanya memeriksa konsistensi dan kewarasan nilai yang diisi. Kelengkapan
 * (semua nilai wajib terisi) diperiksa terpisah oleh [validateCompleteness]
 * karena import awal boleh menyimpan konfigurasi setengah jadi (Schema.md §3).
 */
object MachineConfigValidator {

    const val SUPPORTED_SCHEMA_VERSION: Int = 1
    const val JOINT_COUNT: Int = 3

    fun validate(config: MachineConfig): ValidationResult {
        val problems = buildList {
            if (config.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                add(ConfigProblem("schemaVersion", ConfigProblemReason.UNKNOWN_SCHEMA_VERSION))
            }
            addAll(conveyorProblems(config.conveyor))
            addAll(robotProblems(config.robots))
        }
        return problems.toResult()
    }

    private fun conveyorProblems(conveyor: ConveyorConfig): List<ConfigProblem> = buildList {
        conveyor.surfaceWidthMm?.let {
            if (!it.isFinite()) add(ConfigProblem("conveyor.surfaceWidthMm", ConfigProblemReason.NOT_FINITE))
            else if (it <= 0) add(ConfigProblem("conveyor.surfaceWidthMm", ConfigProblemReason.NEGATIVE_OR_ZERO))
        }
        conveyor.turnRadiusMm?.let {
            if (!it.isFinite()) add(ConfigProblem("conveyor.turnRadiusMm", ConfigProblemReason.NOT_FINITE))
            else if (it <= 0) add(ConfigProblem("conveyor.turnRadiusMm", ConfigProblemReason.NEGATIVE_OR_ZERO))
        }
        conveyor.segmentLengthsMm.forEachIndexed { index, length ->
            val field = "conveyor.segmentLengthsMm[$index]"
            if (!length.isFinite()) add(ConfigProblem(field, ConfigProblemReason.NOT_FINITE))
            else if (length <= 0) add(ConfigProblem(field, ConfigProblemReason.NEGATIVE_OR_ZERO))
        }
        conveyor.encoderTicksPerMeter?.let {
            when {
                !it.isFinite() -> add(ConfigProblem("conveyor.encoderTicksPerMeter", ConfigProblemReason.NOT_FINITE))
                it == 0.0 -> add(ConfigProblem("conveyor.encoderTicksPerMeter", ConfigProblemReason.ENCODER_FACTOR_ZERO))
                it < 0 -> add(ConfigProblem("conveyor.encoderTicksPerMeter", ConfigProblemReason.NEGATIVE))
            }
        }
        val min = conveyor.speedMinMetersPerSecond
        val max = conveyor.speedMaxMetersPerSecond
        if (min != null && max != null) {
            when {
                !min.isFinite() || !max.isFinite() ->
                    add(ConfigProblem("conveyor.speedRange", ConfigProblemReason.NOT_FINITE))
                min <= 0 -> add(ConfigProblem("conveyor.speedMinMetersPerSecond", ConfigProblemReason.NEGATIVE_OR_ZERO))
                min > max -> add(ConfigProblem("conveyor.speedRange", ConfigProblemReason.INVERTED_RANGE))
            }
        }
        addAll(polygonProblems("conveyor.detectionArea", conveyor.detectionArea))
        addAll(polygonProblems("conveyor.pickupArea", conveyor.pickupArea))
    }

    private fun robotProblems(robots: List<RobotConfig>): List<ConfigProblem> = buildList {
        if (robots.map { it.robotId }.distinct().size != robots.size) {
            add(ConfigProblem("robots", ConfigProblemReason.DUPLICATE_ROBOT))
        }
        robots.forEach { robot ->
            val prefix = "robots.${robot.robotId.name.lowercase()}"
            if (robot.segmentLengthsMm.isNotEmpty() && robot.segmentLengthsMm.size != JOINT_COUNT) {
                add(ConfigProblem("$prefix.segmentLengthsMm", ConfigProblemReason.WRONG_SEGMENT_COUNT))
            }
            robot.segmentLengthsMm.forEachIndexed { index, length ->
                val field = "$prefix.segmentLengthsMm[$index]"
                if (!length.isFinite()) add(ConfigProblem(field, ConfigProblemReason.NOT_FINITE))
                else if (length <= 0) add(ConfigProblem(field, ConfigProblemReason.NEGATIVE_OR_ZERO))
            }
            robot.mountHeightMm?.let {
                if (!it.isFinite()) add(ConfigProblem("$prefix.mountHeightMm", ConfigProblemReason.NOT_FINITE))
                else if (it < 0) add(ConfigProblem("$prefix.mountHeightMm", ConfigProblemReason.NEGATIVE))
            }
            if (robot.jointLimitsDeg.isNotEmpty() && robot.jointLimitsDeg.size != JOINT_COUNT) {
                add(ConfigProblem("$prefix.jointLimitsDeg", ConfigProblemReason.WRONG_JOINT_COUNT))
            }
            robot.baseLimitsDeg?.let { addAll(rangeProblems("$prefix.baseLimitsDeg", it)) }
            robot.jointLimitsDeg.forEachIndexed { index, range ->
                addAll(rangeProblems("$prefix.jointLimitsDeg[$index]", range))
            }
            robot.gripperOpeningMm?.let {
                if (!it.min.isFinite() || !it.max.isFinite()) {
                    add(ConfigProblem("$prefix.gripperOpeningMm", ConfigProblemReason.NOT_FINITE))
                } else if (it.min <= 0) {
                    add(ConfigProblem("$prefix.gripperOpeningMm", ConfigProblemReason.NEGATIVE_OR_ZERO))
                } else if (it.min > it.max) {
                    add(ConfigProblem("$prefix.gripperOpeningMm", ConfigProblemReason.INVERTED_RANGE))
                }
            }
            robot.maxTargetSizeMm?.let {
                if (!it.min.isFinite() || !it.max.isFinite()) {
                    add(ConfigProblem("$prefix.maxTargetSizeMm", ConfigProblemReason.NOT_FINITE))
                } else if (it.min <= 0) {
                    add(ConfigProblem("$prefix.maxTargetSizeMm", ConfigProblemReason.NEGATIVE_OR_ZERO))
                } else if (it.min > it.max) {
                    add(ConfigProblem("$prefix.maxTargetSizeMm", ConfigProblemReason.INVERTED_RANGE))
                }
            }
            addAll(polygonProblems("$prefix.pickupArea", robot.pickupArea))
            robot.binPositions.keys.forEach { binId ->
                if (binId !in robot.robotId.binIds) {
                    add(ConfigProblem("$prefix.binPositions.$binId", ConfigProblemReason.BIN_NOT_OWNED_BY_ROBOT))
                }
            }
            robot.motionParameters.forEach { (name, value) ->
                if (!value.isFinite()) {
                    add(ConfigProblem("$prefix.motionParameters.$name", ConfigProblemReason.NOT_FINITE))
                }
            }
        }
    }

    private fun rangeProblems(field: String, range: AngleRange): List<ConfigProblem> = buildList {
        if (!range.minDeg.isFinite() || !range.maxDeg.isFinite()) {
            add(ConfigProblem(field, ConfigProblemReason.NOT_FINITE))
        } else if (range.minDeg > range.maxDeg) {
            add(ConfigProblem(field, ConfigProblemReason.INVERTED_RANGE))
        }
    }

    private fun polygonProblems(field: String, points: List<PointMm>): List<ConfigProblem> = buildList {
        if (points.isEmpty()) return@buildList
        if (points.size < 3) {
            add(ConfigProblem(field, ConfigProblemReason.DEGENERATE_POLYGON))
            return@buildList
        }
        if (points.any { !it.x.isFinite() || !it.y.isFinite() }) {
            add(ConfigProblem(field, ConfigProblemReason.NOT_FINITE))
            return@buildList
        }
        val distinct = points.distinct()
        if (distinct.size < 3) add(ConfigProblem(field, ConfigProblemReason.DEGENERATE_POLYGON))
    }

    /**
     * Kelengkapan nilai yang wajib ada sebelum pemilahan dimulai. Kelas aktif
     * tanpa tujuan bak valid memblokir start (MOD-05, invarian 9).
     */
    fun validateCompleteness(config: MachineConfig): ValidationResult {
        val problems = buildList {
            if (config.conveyor.encoderTicksPerMeter == null) {
                add(ConfigProblem("conveyor.encoderTicksPerMeter", ConfigProblemReason.NOT_CONFIGURED))
            }
            if (config.conveyor.direction == null) {
                add(ConfigProblem("conveyor.direction", ConfigProblemReason.NOT_CONFIGURED))
            }
            if (config.robots.none { it.enabled }) {
                add(ConfigProblem("robots", ConfigProblemReason.NOT_CONFIGURED))
            }
        }
        return problems.toResult()
    }

    private fun List<ConfigProblem>.toResult(): ValidationResult =
        if (isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(this)
}
