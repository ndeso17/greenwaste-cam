package id.pemilah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pratinjau memakai `FILL_CENTER`: isi gambar menutupi bingkai lalu dipotong di
 * tengah. Uji ini menjaga transformasi overlay tetap mengikuti aturan tersebut.
 */
class FillRectTest {
    @Test
    fun fillRect_landscapeFrame_inTallContainer_cropsLeftAndRight() {
        val rect = fillRect(frameAspect = 4f / 3f, containerWidth = 360f, containerHeight = 640f)
        assertEquals(640f, rect.height, 0.001f)
        assertEquals(640f * 4f / 3f, rect.width, 0.001f)
        assertTrue("harus terpotong di kiri", rect.left < 0f)
        assertTrue("harus terpotong di kanan", rect.right > 360f)
    }

    @Test
    fun fillRect_tallFrame_inWideContainer_cropsTopAndBottom() {
        val rect = fillRect(frameAspect = 3f / 4f, containerWidth = 800f, containerHeight = 400f)
        assertEquals(800f, rect.width, 0.001f)
        assertEquals(800f * 4f / 3f, rect.height, 0.001f)
        assertTrue(rect.top < 0f)
        assertTrue(rect.bottom > 400f)
    }

    @Test
    fun fillRect_matchingAspect_fillsExactly() {
        val rect = fillRect(frameAspect = 2f, containerWidth = 400f, containerHeight = 200f)
        assertEquals(0f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(400f, rect.width, 0.001f)
        assertEquals(200f, rect.height, 0.001f)
    }

    @Test
    fun fillRect_invalidAspect_fallsBackToContainer() {
        val rect = fillRect(frameAspect = Float.NaN, containerWidth = 100f, containerHeight = 50f)
        assertEquals(100f, rect.width, 0.001f)
        assertEquals(50f, rect.height, 0.001f)
    }

    @Test
    fun map_centerOfFrame_mapsToCenterOfContainer() {
        val rect = fillRect(frameAspect = 4f / 3f, containerWidth = 360f, containerHeight = 640f)
        val (x, y) = rect.map(0.5f, 0.5f)
        assertEquals(180f, x, 0.001f)
        assertEquals(320f, y, 0.001f)
    }
}