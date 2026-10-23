package id.pemilah.domain.config

import id.pemilah.config.RobotId
import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private object MappingFixture {
    val leftOnly = setOf(RobotId.LEFT)
    val bothRobots = setOf(RobotId.LEFT, RobotId.RIGHT)

    /** Enam kelas ke enam bak: organik/plastik/kaleng kiri, styrofoam/kaca/kardus kanan. */
    val sixBins = mapOf(
        WasteClass.ORGANIK to "B1",
        WasteClass.PLASTIK to "B2",
        WasteClass.KALENG to "B3",
        WasteClass.STYROFOAM to "B4",
        WasteClass.KACA to "B5",
        WasteClass.KARDUS to "B6",
        WasteClass.BESI to null,
    )
}

class BinMappingTest {

    @Test
    fun resolutionFor_assignedLeftBin_returnsLeftRobot() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.bothRobots)

        val resolution = mapping.resolutionFor(WasteClass.PLASTIK)

        assertEquals(
            BinResolution.Assigned(BinDestination(BinId.B2, RobotId.LEFT)),
            resolution,
        )
    }

    @Test
    fun resolutionFor_nullAssignment_isBlockedNoBin() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.bothRobots)

        val resolution = mapping.resolutionFor(WasteClass.BESI)

        assertEquals(BinResolution.Blocked(BinBlockReason.NO_BIN_ASSIGNED), resolution)
    }

    @Test
    fun resolutionFor_unknownBinValue_isBlockedUnknownBin() {
        val mapping = BinMapping(
            mapOf(WasteClass.KACA to "B7"),
            MappingFixture.bothRobots,
        )

        val resolution = mapping.resolutionFor(WasteClass.KACA)

        assertEquals(BinResolution.Blocked(BinBlockReason.UNKNOWN_BIN), resolution)
    }

    @Test
    fun resolutionFor_binOwnedByDisabledRobot_isBlocked() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.leftOnly)

        val resolution = mapping.resolutionFor(WasteClass.STYROFOAM)

        assertEquals(BinResolution.Blocked(BinBlockReason.ROBOT_DISABLED), resolution)
    }

    @Test
    fun blockersFor_activeBesi_isReportedBecauseNoBin() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.bothRobots)

        val blockers = mapping.blockersFor(setOf(WasteClass.PLASTIK, WasteClass.BESI))

        assertEquals(listOf(WasteClass.BESI), blockers)
    }

    @Test
    fun blockersFor_activeClassesOnDisabledRobot_areReported() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.leftOnly)

        val blockers = mapping.blockersFor(setOf(WasteClass.KACA, WasteClass.ORGANIK))

        assertEquals(listOf(WasteClass.KACA), blockers)
    }

    @Test
    fun blockersFor_allActiveClassesAssigned_isEmpty() {
        val mapping = BinMapping(MappingFixture.sixBins, MappingFixture.bothRobots)

        val blockers = mapping.blockersFor(
            setOf(WasteClass.ORGANIK, WasteClass.STYROFOAM, WasteClass.KARDUS),
        )

        assertTrue(blockers.isEmpty())
    }

    @Test
    fun problems_listsEveryUnassignedOrInvalidClass() {
        val mapping = BinMapping(
            mapOf(WasteClass.ORGANIK to "B1", WasteClass.PLASTIK to "B9"),
            MappingFixture.leftOnly,
        )

        val problems = mapping.problems()

        assertEquals(WasteClass.entries.size - 1, problems.size)
        assertTrue(
            problems.contains(MappingProblem(WasteClass.PLASTIK, BinBlockReason.UNKNOWN_BIN)),
        )
        assertTrue(
            problems.contains(MappingProblem(WasteClass.KACA, BinBlockReason.NO_BIN_ASSIGNED)),
        )
        assertTrue(problems.none { it.wasteClass == WasteClass.ORGANIK })
    }

    @Test
    fun binId_of_outOfRangeValue_isNull() {
        assertEquals(null, BinId.of("B0"))
        assertEquals(null, BinId.of("B7"))
        assertEquals(null, BinId.of("b1"))
        assertEquals(null, BinId.of(null))
        assertEquals(BinId.B6, BinId.of("B6"))
    }

    @Test
    fun ownerOf_bins_areSplitBetweenRobots() {
        assertEquals(RobotId.LEFT, BinId.ownerOf(BinId.B1))
        assertEquals(RobotId.LEFT, BinId.ownerOf(BinId.B3))
        assertEquals(RobotId.RIGHT, BinId.ownerOf(BinId.B4))
        assertEquals(RobotId.RIGHT, BinId.ownerOf(BinId.B6))
    }
}
