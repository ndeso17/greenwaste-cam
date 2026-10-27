package id.pemilah.ui

import id.pemilah.inference.AnalyzedObject
import id.pemilah.inference.CorrectionMatcher
import id.pemilah.inference.NormalizedRegion
import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.*

class TestSampleDeduplicatorTest {
    private val original = AnalyzedObject(
        NormalizedRegion(0.1f, 0.1f, 0.4f, 0.4f), "Kaca", 0.9f,
        List(CorrectionMatcher.DESCRIPTOR_SIZE) { 0.5f },
    )
    private val other = original.copy(region = NormalizedRegion(0.6f, 0.1f, 0.9f, 0.4f))

    @Test fun actualStationaryPhoneSequenceProducesOnlyOneSample() {
        val resource = requireNotNull(javaClass.getResource("/stationary-phone-objects.json"))
        val frames = Json.parseToJsonElement(resource.readText()).jsonArray
        val gate = TestSampleDeduplicator()
        var saved = 0
        for (frame in frames) {
            val objects = frame.jsonArray.map { element ->
                val obj = element.jsonObject
                fun value(key: String) = obj.getValue(key).jsonPrimitive.float
                AnalyzedObject(
                    NormalizedRegion(value("left"), value("top"), value("right"), value("bottom")),
                    obj.getValue("predictedLabel").jsonPrimitive.content, value("probability"),
                    obj.getValue("descriptor").jsonArray.map { it.jsonPrimitive.float },
                )
            }
            if (gate.shouldSave(objects)) { saved++; gate.markSaved() }
        }
        assertEquals("Real stationary camera sequence must be saved once", 1, saved)
    }

    @Test fun stationaryObjectAndPredictionChangesDoNotCreateDuplicates() {
        val gate = TestSampleDeduplicator()
        assertTrue(gate.shouldSave(listOf(original)))
        gate.markSaved()
        repeat(100) {
            assertFalse(gate.shouldSave(listOf(original.copy(originalLabel = "Plastik", probability = 0.7f))))
        }
    }

    @Test fun smallBoxAndColorJitterDoesNotCreateDuplicates() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        val jitter = original.copy(
            region = NormalizedRegion(0.105f, 0.1f, 0.405f, 0.4f),
            descriptor = original.descriptor.map { it + 0.01f },
        )
        assertFalse(gate.shouldSave(listOf(jitter)))
    }

    @Test fun newObjectTriggersOneSaveRegardlessOfDetectionOrder() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        assertTrue(gate.shouldSave(listOf(other, original)))
        gate.markSaved()
        assertFalse(gate.shouldSave(listOf(original, other)))
        assertFalse(gate.shouldSave(listOf(other)))
    }

    @Test fun movementOrChangedAppearanceCreatesSample() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        assertTrue(gate.shouldSave(listOf(other)))
        gate.markSaved()
        val changed = other.copy(descriptor = List(CorrectionMatcher.DESCRIPTOR_SIZE) { 1f })
        assertTrue(gate.shouldSave(listOf(changed)))
        gate.markSaved()
        assertFalse(gate.shouldSave(listOf(changed)))
    }

    @Test fun briefMissIsIgnoredButReappearanceAfterAbsenceIsNew() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        repeat(2) { assertFalse(gate.shouldSave(emptyList())) }
        assertFalse(gate.shouldSave(listOf(original)))
        repeat(3) { assertFalse(gate.shouldSave(emptyList())) }
        assertTrue(gate.shouldSave(listOf(original)))
    }

    @Test fun failedSaveCanBeRetriedAndResetStartsFreshSession() {
        val gate = TestSampleDeduplicator()
        assertTrue(gate.shouldSave(listOf(original)))
        assertTrue(gate.shouldSave(listOf(original)))
        gate.markSaved()
        assertFalse(gate.shouldSave(listOf(original)))
        gate.reset()
        assertTrue(gate.shouldSave(listOf(original)))
    }

    @Test fun oneOldObjectCannotHideTwoCurrentObjects() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        assertTrue(gate.shouldSave(listOf(original, original)))
    }

    @Test fun slowMovementIsComparedAgainstSavedPosition() {
        val gate = TestSampleDeduplicator()
        gate.shouldSave(listOf(original)); gate.markSaved()
        var newSample = false
        for (step in 1..10) {
            val offset = step * 0.01f
            newSample = newSample || gate.shouldSave(listOf(original.copy(
                region = NormalizedRegion(0.1f + offset, 0.1f, 0.4f + offset, 0.4f),
            )))
        }
        assertTrue(newSample)
    }
}
