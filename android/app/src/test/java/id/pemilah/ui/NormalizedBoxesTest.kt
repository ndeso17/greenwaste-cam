package id.pemilah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedBoxesTest {
    @Test
    fun objectAnchor_centersOnBoxMiddle() {
        val (x, y) = objectAnchor(NormalizedBox(0f, 0f, 1f, 1f))
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(0.5f, y, 0.0001f)
    }

    @Test
    fun objectAnchor_staysInsideUnitSquare() {
        val (x, y) = objectAnchor(NormalizedBox(-0.2f, 0.9f, 0.4f, 1.4f))
        assertTrue(x in 0f..1f)
        assertTrue(y in 0f..1f)
    }

    @Test
    fun fitRect_keepsRequestedAspectAndStaysInsideContainer() {
        val rect = fitRect(aspect = 4f / 3f, containerWidth = 200f, containerHeight = 400f)
        assertEquals(4f / 3f, rect.width / rect.height, 0.001f)
        assertTrue(rect.left >= 0f && rect.top >= 0f)
        assertTrue(rect.right <= 200f && rect.bottom <= 400f)
        assertEquals(200f, rect.width, 0.001f)
    }

    @Test
    fun fitRect_invalidAspect_isEmptyInsteadOfGuessing() {
        val rect = fitRect(aspect = 0f, containerWidth = 100f, containerHeight = 100f)
        assertEquals(0f, rect.width, 0f)
        assertEquals(0f, rect.height, 0f)
    }
}