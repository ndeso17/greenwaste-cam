package id.pemilah.ui

import id.pemilah.storage.SampleRecord
import id.pemilah.storage.SampleObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GallerySelectionTest {
    private fun record(id: String, objectIds: List<String>) = SampleRecord(
        id = id, sessionId = "session", modelId = "model", modelName = "model",
        capturedAt = 1L, width = 10, height = 10,
        objects = objectIds.map { SampleObject(it, 0f, 0f, 1f, 1f, "Kaca", 0.7f, List(192) { 0.5f }) },
    )
    private val records = listOf(record("s1", listOf("0", "1")), record("s2", listOf("0")))

    @Test
    fun toggleSample_selectsEveryObjectOfThatImage() {
        val selection = GallerySelection().toggleSample("s1")
        assertTrue(selection.isSampleSelected("s1"))
        assertTrue(selection.isObjectSelected("s1", "1"))
        assertEquals(2, selection.selectedObjectCount(records))
        assertEquals(2, selection.correctionTargets(records).size)
    }

    @Test
    fun toggleObject_selectsOnlyThatObject() {
        val selection = GallerySelection().toggleObject("s1", "1", setOf("0", "1"))
        assertFalse(selection.isSampleSelected("s1"))
        assertTrue(selection.isObjectSelected("s1", "1"))
        assertFalse(selection.isObjectSelected("s1", "0"))
        assertEquals(1, selection.selectedObjectCount(records))
    }

    @Test
    fun selectingEveryObject_promotesToFullImage() {
        val selection = GallerySelection()
            .toggleObject("s1", "0", setOf("0", "1"))
            .toggleObject("s1", "1", setOf("0", "1"))
        assertTrue(selection.isSampleSelected("s1"))
        assertEquals(2, selection.selectedObjectCount(records))
    }

    @Test
    fun deselectingOneObjectFromFullImage_keepsTheRest() {
        val selection = GallerySelection().toggleSample("s1").toggleObject("s1", "0", setOf("0", "1"))
        assertFalse(selection.isSampleSelected("s1"))
        assertTrue(selection.isObjectSelected("s1", "1"))
        assertFalse(selection.isObjectSelected("s1", "0"))
        assertEquals(1, selection.selectedObjectCount(records))
    }

    @Test
    fun deselectingLastObject_removesImageFromSelection() {
        val selection = GallerySelection()
            .toggleObject("s2", "0", setOf("0"))
            .let { full -> full.toggleObject("s2", "0", setOf("0")) }
        assertTrue(selection.isEmpty)
        assertTrue(selection.selectedSampleIds(records).isEmpty())
    }

    @Test
    fun selectAllAndClear_coverWholeGallery() {
        val all = GallerySelection().selectAll(records)
        assertEquals(3, all.selectedObjectCount(records))
        assertEquals(listOf("s1", "s2"), all.selectedSampleIds(records))
        assertTrue(all.clear().isEmpty)
    }
}