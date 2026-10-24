package id.pemilah.storage

import id.pemilah.calibration.CalibrationValidity
import id.pemilah.calibration.CameraCalibration
import id.pemilah.calibration.MappingMethod
import id.pemilah.calibration.ReferencePoint
import id.pemilah.calibration.ValidityReason
import id.pemilah.config.AngleRange
import id.pemilah.config.ConveyorConfig
import id.pemilah.config.ConveyorDirection
import id.pemilah.config.MachineConfig
import id.pemilah.config.PointMm
import id.pemilah.config.Range
import id.pemilah.config.RobotConfig
import id.pemilah.config.RobotId
import id.pemilah.config.RotationDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private object StoreFixture {
    const val CAPTURE_WIDTH_PX = 1280
    const val CAPTURE_HEIGHT_PX = 720

    val config = MachineConfig(
        schemaVersion = 1,
        conveyor = ConveyorConfig(
            surfaceWidthMm = 350.0,
            turnRadiusMm = 120.0,
            segmentLengthsMm = listOf(500.0, 400.0),
            direction = ConveyorDirection.COUNTERCLOCKWISE,
            speedMinMetersPerSecond = 0.05,
            speedMaxMetersPerSecond = 0.30,
            encoderTicksPerMeter = 1200.0,
            detectionArea = listOf(PointMm(0.0, 0.0), PointMm(10.0, 0.0), PointMm(10.0, 20.0)),
            pickupArea = listOf(PointMm(1.0, 2.0)),
        ),
        robots = listOf(
            RobotConfig(
                robotId = RobotId.RIGHT,
                enabled = true,
                basePosition = PointMm(15.0, 25.0),
                segmentLengthsMm = listOf(120.0, 100.0),
                mountHeightMm = 80.0,
                zeroAnglesDeg = listOf(0.0, 90.0),
                rotationDirections = listOf(RotationDirection.POSITIVE, RotationDirection.NEGATIVE),
                baseLimitsDeg = AngleRange(-90.0, 90.0),
                jointLimitsDeg = listOf(AngleRange(0.0, 120.0)),
                gripperOpeningMm = Range(5.0, 60.0),
                maxTargetSizeMm = Range(10.0, 90.0),
                pickupArea = listOf(PointMm(3.0, 4.0)),
                binPositions = mapOf("B4" to PointMm(7.0, 8.0)),
                motionParameters = mapOf("speed" to 0.4, "acceleration" to 0.6),
            ),
        ),
    )

    val calibration = CameraCalibration(
        schemaVersion = 1,
        captureWidthPx = CAPTURE_WIDTH_PX,
        captureHeightPx = CAPTURE_HEIGHT_PX,
        rotationDegrees = 90,
        referencePoints = listOf(
            ReferencePoint(10.0, 20.0, 0.0, 0.0),
            ReferencePoint(30.0, 40.0, 100.0, 0.0),
            ReferencePoint(50.0, 60.0, 0.0, 100.0),
        ),
        mappingMethod = MappingMethod.HOMOGRAPHY,
        reprojectionError = 0.75,
        validity = CalibrationValidity(
            state = id.pemilah.calibration.ValidityState.NEEDS_CHECK,
            reason = ValidityReason.GEOMETRY_CHANGED,
            checkedAtEpochMs = 1_700_000_000_000L,
            checkedNote = "dipindahkan",
        ),
    )

    class MemoryStorage : ConfigStorage {
        val files = mutableMapOf<String, String>()
        var failWrite = false
        val replaced = mutableListOf<Pair<String, String>>()

        override fun read(name: String): String? = files[name]

        override fun write(name: String, content: String) {
            if (failWrite) error("disk penuh")
            files[name] = content
        }

        override fun replace(from: String, to: String) {
            replaced.add(from to to)
            files[to] = files[from] ?: return
            files.remove(from)
        }

        override fun exists(name: String): Boolean = files.containsKey(name)
    }
}

class ConfigStoreTest {

    @Test
    fun config_roundTrip_preservesEveryValue() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        assertTrue(store.saveConfig(StoreFixture.config) is SaveResult.Saved)
        val loaded = store.loadConfig()

        assertTrue(loaded is LoadResult.Loaded)
        assertEquals(StoreFixture.config, (loaded as LoadResult.Loaded).value)
    }

    @Test
    fun config_roundTrip_preservesUnconfiguredValuesAsNull() {
        val store = ConfigStore(StoreFixture.MemoryStorage())
        val sparse = MachineConfig(
            schemaVersion = 1,
            conveyor = ConveyorConfig(),
            robots = listOf(RobotConfig(robotId = RobotId.LEFT)),
        )

        store.saveConfig(sparse)
        val loaded = (store.loadConfig() as LoadResult.Loaded).value

        assertEquals(sparse, loaded)
        assertNull(loaded.conveyor.encoderTicksPerMeter)
        assertNull(loaded.robots.single().basePosition)
    }

    @Test
    fun config_absentFile_returnsAbsent() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        assertEquals(LoadResult.Absent, store.loadConfig())
    }

    @Test
    fun config_corruptedNumber_returnsCorruptedWithKey() {
        val storage = StoreFixture.MemoryStorage()
        storage.files[ConfigStore.CONFIG_FILE] =
            "schemaVersion=1\nconveyor.encoderTicksPerMeter=dua belas\nrobots=0\n"
        val store = ConfigStore(storage)

        val result = store.loadConfig()

        assertTrue(result is LoadResult.Corrupted)
        val problems = (result as LoadResult.Corrupted).problems
        assertTrue(problems.any { it.key == "conveyor.encoderTicksPerMeter" })
        assertEquals(StoreProblemReason.NOT_A_NUMBER, problems.first().reason)
    }

    @Test
    fun config_malformedLine_returnsCorrupted() {
        val storage = StoreFixture.MemoryStorage()
        storage.files[ConfigStore.CONFIG_FILE] = "schemaVersion=1\ntanpa pemisah\n"
        val store = ConfigStore(storage)

        val result = store.loadConfig()

        assertTrue(result is LoadResult.Corrupted)
        assertEquals(
            StoreProblemReason.MALFORMED_LINE,
            (result as LoadResult.Corrupted).problems.single().reason,
        )
    }

    @Test
    fun config_unknownEnumValue_returnsCorrupted() {
        val storage = StoreFixture.MemoryStorage()
        storage.files[ConfigStore.CONFIG_FILE] = "schemaVersion=1\nconveyor.direction=SIDEWAYS\nrobots=0\n"
        val store = ConfigStore(storage)

        val result = store.loadConfig()

        assertTrue(result is LoadResult.Corrupted)
        assertTrue(
            (result as LoadResult.Corrupted).problems.any {
                it.reason == StoreProblemReason.UNKNOWN_ENUM_VALUE
            },
        )
    }

    @Test
    fun config_writeFailure_returnsFailedNotSaved() {
        val storage = StoreFixture.MemoryStorage().apply { failWrite = true }
        val store = ConfigStore(storage)

        val result = store.saveConfig(StoreFixture.config)

        assertTrue(result is SaveResult.Failed)
        assertEquals(StoreProblemReason.WRITE_FAILED, (result as SaveResult.Failed).reason)
        assertNull(storage.files[ConfigStore.CONFIG_FILE])
    }

    @Test
    fun config_saveIsAtomic_replacesTemporaryFile() {
        val storage = StoreFixture.MemoryStorage()
        val store = ConfigStore(storage)

        store.saveConfig(StoreFixture.config)

        assertEquals(listOf("${ConfigStore.CONFIG_FILE}.tmp" to ConfigStore.CONFIG_FILE), storage.replaced)
        assertTrue(storage.files.containsKey(ConfigStore.CONFIG_FILE))
        assertTrue(storage.files.keys.none { it.endsWith(".tmp") })
    }

    @Test
    fun calibration_roundTrip_preservesEveryValue() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        store.saveCalibration(StoreFixture.calibration)
        val loaded = store.loadCalibration()

        assertTrue(loaded is LoadResult.Loaded)
        assertEquals(StoreFixture.calibration, (loaded as LoadResult.Loaded).value)
    }

    @Test
    fun calibration_absentFile_returnsAbsent() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        assertEquals(LoadResult.Absent, store.loadCalibration())
    }

    @Test
    fun calibration_missingMethodStaysNull() {
        val store = ConfigStore(StoreFixture.MemoryStorage())
        val withoutMethod = StoreFixture.calibration.copy(mappingMethod = null, reprojectionError = null)

        store.saveCalibration(withoutMethod)
        val loaded = (store.loadCalibration() as LoadResult.Loaded).value

        assertNull(loaded.mappingMethod)
        assertNull(loaded.reprojectionError)
    }

    @Test
    fun calibration_corruptedGeometry_returnsCorrupted() {
        val storage = StoreFixture.MemoryStorage()
        storage.files[ConfigStore.CALIBRATION_FILE] =
            "schemaVersion=1\ncaptureWidthPx=1280\ncaptureHeightPx=720\nrotationDegrees=0\n" +
                "referencePoints=10,20,0\nvalidityState=VALID\n"
        val store = ConfigStore(storage)

        val result = store.loadCalibration()

        assertTrue(result is LoadResult.Corrupted)
        assertTrue(
            (result as LoadResult.Corrupted).problems.any { it.key == "referencePoints" },
        )
    }

    @Test
    fun profileFile_storedAndReadBack() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        assertEquals(SaveResult.Saved, store.writeProfileFile("profil-sampah.txt", "isi-profil"))
        assertEquals("isi-profil", store.readProfileFile("profil-sampah.txt"))
    }

    @Test
    fun profileFile_missing_returnsNull() {
        val store = ConfigStore(StoreFixture.MemoryStorage())

        assertNull(store.readProfileFile("tidak-ada.txt"))
    }
}
