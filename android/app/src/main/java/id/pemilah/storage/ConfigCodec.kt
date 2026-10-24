package id.pemilah.storage

import id.pemilah.calibration.CalibrationValidity
import id.pemilah.calibration.CameraCalibration
import id.pemilah.calibration.MappingMethod
import id.pemilah.calibration.ReferencePoint
import id.pemilah.calibration.ValidityReason
import id.pemilah.calibration.ValidityState
import id.pemilah.config.AngleRange
import id.pemilah.config.ConveyorConfig
import id.pemilah.config.ConveyorDirection
import id.pemilah.config.MachineConfig
import id.pemilah.config.PointMm
import id.pemilah.config.Range
import id.pemilah.config.RobotConfig
import id.pemilah.config.RobotId
import id.pemilah.config.RotationDirection

/**
 * Codec teks kanonik untuk [MachineConfig] dan [CameraCalibration] (NFR-03,
 * CFG-01, CFG-02).
 *
 * Bentuk berkas sengaja sederhana dan tanpa pustaka JSON tambahan (D-05):
 * `key=value` per baris, `|` sebagai pemisah daftar, `#` sebagai komentar, dan
 * `value` kosong berarti `null` (belum dikonfigurasi). Aturan penting:
 *
 * - `null` tidak pernah ditulis sebagai `0`/`""`; tipe bawaan tidak boleh
 *   menyamar sebagai angka mesin (Rules.md §2.2).
 * - Kunci tak dikenal ditolak supaya berkas versi lebih baru tidak dibaca
 *   sebagian dan diam-diam kehilangan nilai.
 * - Nilai rusak dilaporkan sebagai [StoreProblem] dengan kunci yang salah, bukan
 *   diganti nilai bawaan.
 */
object ConfigCodec {

    private const val SEP = "|"
    private const val FIELD_EQUALS = "="

    fun encodeConfig(config: MachineConfig): String = buildString {
        line("schemaVersion", config.schemaVersion)
        line("conveyor.surfaceWidthMm", config.conveyor.surfaceWidthMm)
        line("conveyor.turnRadiusMm", config.conveyor.turnRadiusMm)
        line("conveyor.segmentLengthsMm", config.conveyor.segmentLengthsMm.joinToString(SEP))
        line("conveyor.direction", config.conveyor.direction?.name)
        line("conveyor.speedMinMetersPerSecond", config.conveyor.speedMinMetersPerSecond)
        line("conveyor.speedMaxMetersPerSecond", config.conveyor.speedMaxMetersPerSecond)
        line("conveyor.encoderTicksPerMeter", config.conveyor.encoderTicksPerMeter)
        line("conveyor.detectionArea", encodePoints(config.conveyor.detectionArea))
        line("conveyor.pickupArea", encodePoints(config.conveyor.pickupArea))
        line("robots", config.robots.size)
        config.robots.forEachIndexed { index, robot -> encodeRobot(index, robot) }
    }

    fun decodeConfig(text: String): CodecResult<MachineConfig> {
        val fields = parse(text) ?: return CodecResult.Failure(listOf(StoreProblem("", StoreProblemReason.MALFORMED_LINE)))
        val problems = mutableListOf<StoreProblem>()
        val schemaVersion = fields.int("schemaVersion", problems)

        val conveyor = ConveyorConfig(
            surfaceWidthMm = fields.double("conveyor.surfaceWidthMm", problems),
            turnRadiusMm = fields.double("conveyor.turnRadiusMm", problems),
            segmentLengthsMm = fields.doubleList("conveyor.segmentLengthsMm", problems),
            direction = fields.enumOrNull("conveyor.direction", ConveyorDirection.entries, problems),
            speedMinMetersPerSecond = fields.double("conveyor.speedMinMetersPerSecond", problems),
            speedMaxMetersPerSecond = fields.double("conveyor.speedMaxMetersPerSecond", problems),
            encoderTicksPerMeter = fields.double("conveyor.encoderTicksPerMeter", problems),
            detectionArea = decodePoints(fields["conveyor.detectionArea"], "conveyor.detectionArea", problems),
            pickupArea = decodePoints(fields["conveyor.pickupArea"], "conveyor.pickupArea", problems),
        )

        val robotCount = fields.int("robots", problems) ?: 0
        val robots = (0 until robotCount).map { index -> decodeRobot(index, fields, problems) }

        return if (problems.isEmpty() && schemaVersion != null) {
            CodecResult.Success(MachineConfig(schemaVersion, conveyor, robots))
        } else {
            CodecResult.Failure(problems)
        }
    }

    fun encodeCalibration(calibration: CameraCalibration): String = buildString {
        line("schemaVersion", calibration.schemaVersion)
        line("captureWidthPx", calibration.captureWidthPx)
        line("captureHeightPx", calibration.captureHeightPx)
        line("rotationDegrees", calibration.rotationDegrees)
        line("mappingMethod", calibration.mappingMethod?.name)
        line("reprojectionError", calibration.reprojectionError)
        line("validityState", calibration.validity.state.name)
        line("validityReason", calibration.validity.reason?.name)
        line("validityCheckedAtEpochMs", calibration.validity.checkedAtEpochMs)
        line("validityCheckedNote", calibration.validity.checkedNote)
        line(
            "referencePoints",
            calibration.referencePoints.joinToString(SEP) {
                listOf(it.imageX, it.imageY, it.worldX, it.worldY).joinToString(",")
            },
        )
    }

    fun decodeCalibration(text: String): CodecResult<CameraCalibration> {
        val fields = parse(text) ?: return CodecResult.Failure(listOf(StoreProblem("", StoreProblemReason.MALFORMED_LINE)))
        val problems = mutableListOf<StoreProblem>()

        val schemaVersion = fields.int("schemaVersion", problems) ?: 0
        val pointCoords = decodeQuadruples(fields["referencePoints"], "referencePoints", problems)
        val validityState = fields.enumOrNull("validityState", ValidityState.entries, problems)
            ?: ValidityState.NEEDS_CHECK
        val calibration = CameraCalibration(
            schemaVersion = schemaVersion,
            captureWidthPx = fields.int("captureWidthPx", problems) ?: 0,
            captureHeightPx = fields.int("captureHeightPx", problems) ?: 0,
            rotationDegrees = fields.int("rotationDegrees", problems) ?: 0,
            referencePoints = pointCoords,
            mappingMethod = fields.enumOrNull("mappingMethod", MappingMethod.entries, problems),
            reprojectionError = fields.double("reprojectionError", problems),
            validity = CalibrationValidity(
                state = validityState,
                reason = fields.enumOrNull("validityReason", ValidityReason.entries, problems),
                checkedAtEpochMs = fields.longOrNull("validityCheckedAtEpochMs", problems),
                checkedNote = fields["validityCheckedNote"],
            ),
        )
        return if (problems.isEmpty()) CodecResult.Success(calibration) else CodecResult.Failure(problems)
    }

    private fun StringBuilder.line(key: String, value: Any?) {
        append(key).append(FIELD_EQUALS).append(value?.toString() ?: "").append('\n')
    }

    private fun StringBuilder.encodeRobot(index: Int, robot: RobotConfig) {
        val prefix = "robot.$index"
        line("$prefix.robotId", robot.robotId.name)
        line("$prefix.enabled", robot.enabled)
        line("$prefix.basePosition", robot.basePosition?.let { "${it.x},${it.y}" })
        line("$prefix.segmentLengthsMm", robot.segmentLengthsMm.joinToString(SEP))
        line("$prefix.mountHeightMm", robot.mountHeightMm)
        line("$prefix.zeroAnglesDeg", robot.zeroAnglesDeg.joinToString(SEP))
        line(
            "$prefix.rotationDirections",
            robot.rotationDirections.joinToString(SEP) { it.name },
        )
        line("$prefix.baseLimitsDeg", robot.baseLimitsDeg?.let { "${it.minDeg},${it.maxDeg}" })
        line(
            "$prefix.jointLimitsDeg",
            robot.jointLimitsDeg.joinToString(SEP) { "${it.minDeg},${it.maxDeg}" },
        )
        line("$prefix.gripperOpeningMm", robot.gripperOpeningMm?.let { "${it.min},${it.max}" })
        line("$prefix.maxTargetSizeMm", robot.maxTargetSizeMm?.let { "${it.min},${it.max}" })
        line("$prefix.pickupArea", encodePoints(robot.pickupArea))
        line(
            "$prefix.binPositions",
            robot.binPositions.entries.joinToString(SEP) { "${it.key},${it.value.x},${it.value.y}" },
        )
        line(
            "$prefix.motionParameters",
            robot.motionParameters.entries.joinToString(SEP) { "${it.key},${it.value}" },
        )
    }

    private fun decodeRobot(
        index: Int,
        fields: Map<String, String>,
        problems: MutableList<StoreProblem>,
    ): RobotConfig {
        val prefix = "robot.$index"
        return RobotConfig(
            robotId = fields.enumOrNull("$prefix.robotId", RobotId.entries, problems)
                ?: RobotId.LEFT,
            enabled = fields.boolean("$prefix.enabled", problems),
            basePosition = decodePoint(fields["$prefix.basePosition"], "$prefix.basePosition", problems),
            segmentLengthsMm = fields.doubleList("$prefix.segmentLengthsMm", problems),
            mountHeightMm = fields.double("$prefix.mountHeightMm", problems),
            zeroAnglesDeg = fields.doubleList("$prefix.zeroAnglesDeg", problems),
            rotationDirections = fields.enumList(
                "$prefix.rotationDirections",
                RotationDirection.entries,
                problems,
            ),
            baseLimitsDeg = decodeAngleRange(fields["$prefix.baseLimitsDeg"], "$prefix.baseLimitsDeg", problems),
            jointLimitsDeg = decodeAngleRangeList(
                fields["$prefix.jointLimitsDeg"],
                "$prefix.jointLimitsDeg",
                problems,
            ),
            gripperOpeningMm = decodeRange(fields["$prefix.gripperOpeningMm"], "$prefix.gripperOpeningMm", problems),
            maxTargetSizeMm = decodeRange(
                fields["$prefix.maxTargetSizeMm"],
                "$prefix.maxTargetSizeMm",
                problems,
            ),
            pickupArea = decodePoints(fields["$prefix.pickupArea"], "$prefix.pickupArea", problems),
            binPositions = decodeBinPositions(fields["$prefix.binPositions"], "$prefix.binPositions", problems),
            motionParameters = decodeDoubles(
                fields["$prefix.motionParameters"],
                "$prefix.motionParameters",
                problems,
            ).toMap(),
        )
    }

    private fun encodePoints(points: List<PointMm>): String =
        points.joinToString(SEP) { "${it.x},${it.y}" }

    private fun parse(text: String): Map<String, String>? {
        val result = mutableMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val separator = line.indexOf(FIELD_EQUALS)
            if (separator <= 0) return null
            val key = line.substring(0, separator).trim()
            if (key.isEmpty()) return null
            result[key] = line.substring(separator + 1)
        }
        return result
    }

    private fun Map<String, String>.raw(key: String): String? = this[key]?.takeIf { it.isNotEmpty() }

    private fun Map<String, String>.int(key: String, problems: MutableList<StoreProblem>): Int? {
        val value = raw(key) ?: return null
        return value.toIntOrNull() ?: run {
            problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
            null
        }
    }

    private fun Map<String, String>.longOrNull(key: String, problems: MutableList<StoreProblem>): Long? {
        val value = raw(key) ?: return null
        return value.toLongOrNull() ?: run {
            problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
            null
        }
    }

    private fun Map<String, String>.double(key: String, problems: MutableList<StoreProblem>): Double? {
        val value = raw(key) ?: return null
        return value.toDoubleOrNull() ?: run {
            problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
            null
        }
    }

    private fun Map<String, String>.boolean(key: String, problems: MutableList<StoreProblem>): Boolean {
        val value = raw(key) ?: return false
        return value.toBooleanStrictOrNull() ?: run {
            problems.add(StoreProblem(key, StoreProblemReason.NOT_A_BOOLEAN))
            false
        }
    }

    private fun Map<String, String>.doubleList(key: String, problems: MutableList<StoreProblem>): List<Double> {
        val value = raw(key) ?: return emptyList()
        return value.split(SEP).mapNotNull { item ->
            item.toDoubleOrNull() ?: run {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            }
        }
    }

    private fun <T : Enum<T>> Map<String, String>.enumOrNull(
        key: String,
        values: List<T>,
        problems: MutableList<StoreProblem>,
    ): T? {
        val value = raw(key) ?: return null
        val match = values.firstOrNull { it.name == value }
        if (match == null) problems.add(StoreProblem(key, StoreProblemReason.UNKNOWN_ENUM_VALUE))
        return match
    }

    private fun <T : Enum<T>> Map<String, String>.enumList(
        key: String,
        values: List<T>,
        problems: MutableList<StoreProblem>,
    ): List<T> {
        val value = raw(key) ?: return emptyList()
        return value.split(SEP).mapNotNull { item ->
            values.firstOrNull { it.name == item } ?: run {
                problems.add(StoreProblem(key, StoreProblemReason.UNKNOWN_ENUM_VALUE))
                null
            }
        }
    }

    private fun decodeDoubles(
        value: String?,
        key: String,
        problems: MutableList<StoreProblem>,
    ): List<Pair<String, Double>> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(SEP).mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 2) {
                problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
                return@mapNotNull null
            }
            val number = parts[1].toDoubleOrNull()
            if (number == null) {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            } else {
                parts[0] to number
            }
        }
    }

    private fun decodePoint(value: String?, key: String, problems: MutableList<StoreProblem>): PointMm? {
        val parts = decodeValues(value, 2, key, problems)
        return if (parts == null || parts.isEmpty()) null else PointMm(parts[0], parts[1])
    }

    private fun decodePoints(value: String?, key: String, problems: MutableList<StoreProblem>): List<PointMm> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(SEP).mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 2) {
                problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
                return@mapNotNull null
            }
            val x = parts[0].toDoubleOrNull()
            val y = parts[1].toDoubleOrNull()
            if (x == null || y == null) {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            } else {
                PointMm(x, y)
            }
        }
    }

    private fun decodeQuadruples(
        value: String?,
        key: String,
        problems: MutableList<StoreProblem>,
    ): List<ReferencePoint> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(SEP).mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 4) {
                problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
                return@mapNotNull null
            }
            val numbers = parts.map { it.toDoubleOrNull() }
            if (numbers.any { it == null }) {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            } else {
                ReferencePoint(numbers[0]!!, numbers[1]!!, numbers[2]!!, numbers[3]!!)
            }
        }
    }

    private fun decodeRange(value: String?, key: String, problems: MutableList<StoreProblem>): Range? {
        val parts = decodeValues(value, 2, key, problems)
        return if (parts == null || parts.isEmpty()) null else Range(parts[0], parts[1])
    }

    private fun decodeAngleRange(value: String?, key: String, problems: MutableList<StoreProblem>): AngleRange? {
        val parts = decodeValues(value, 2, key, problems)
        return if (parts == null || parts.isEmpty()) null else AngleRange(parts[0], parts[1])
    }

    private fun decodeAngleRangeList(value: String?, key: String, problems: MutableList<StoreProblem>): List<AngleRange> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(SEP).mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 2) {
                problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
                return@mapNotNull null
            }
            val min = parts[0].toDoubleOrNull()
            val max = parts[1].toDoubleOrNull()
            if (min == null || max == null) {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            } else {
                AngleRange(min, max)
            }
        }
    }

    private fun decodeBinPositions(value: String?, key: String, problems: MutableList<StoreProblem>): Map<String, PointMm> {
        if (value.isNullOrEmpty()) return emptyMap()
        return value.split(SEP).mapNotNull { item ->
            val parts = item.split(",")
            if (parts.size != 3) {
                problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
                return@mapNotNull null
            }
            val x = parts[1].toDoubleOrNull()
            val y = parts[2].toDoubleOrNull()
            if (x == null || y == null) {
                problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
                null
            } else {
                parts[0] to PointMm(x, y)
            }
        }.toMap()
    }

    private fun decodeValues(value: String?, expected: Int, key: String, problems: MutableList<StoreProblem>): List<Double>? {
        if (value.isNullOrEmpty()) return null
        val parts = value.split(",")
        if (parts.size != expected) {
            problems.add(StoreProblem(key, StoreProblemReason.MALFORMED_LINE))
            return null
        }
        val numbers = parts.mapNotNull { it.toDoubleOrNull() }
        if (numbers.size != expected) {
            problems.add(StoreProblem(key, StoreProblemReason.NOT_A_NUMBER))
            return null
        }
        return numbers
    }
}

sealed interface CodecResult<out T> {
    data class Success<T>(val value: T) : CodecResult<T>
    data class Failure(val problems: List<StoreProblem>) : CodecResult<Nothing>
}

data class StoreProblem(val key: String, val reason: StoreProblemReason)

enum class StoreProblemReason {
    MALFORMED_LINE,
    NOT_A_NUMBER,
    NOT_A_BOOLEAN,
    UNKNOWN_ENUM_VALUE,
    CORRUPTED,
    WRITE_FAILED,
}
