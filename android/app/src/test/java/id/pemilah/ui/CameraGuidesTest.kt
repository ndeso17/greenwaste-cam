package id.pemilah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti garis bantu kamera (instruksi pengguna 27 Oktober 2026).
 *
 * Yang diuji adalah perhitungan posisi garis: dua garis vertikal pada 1/3 dan
 * 2/3 lebar, dua garis horizontal pada 1/3 dan 2/3 tinggi, dan hasil kosong saat
 * bidang belum berukuran. Garis ini panduan bidik, bukan geometri mesin.
 */
class CameraGuidesTest {

    @Test
    fun thirds_placesLinesAtOneThirdAndTwoThirds() {
        val segments = CameraGuides.thirds(width = 900f, height = 600f)

        assertEquals(4, segments.size)
        val vertical = segments.filter { it.startX == it.endX }.map { it.startX }
        val horizontal = segments.filter { it.startY == it.endY }.map { it.startY }
        assertEquals(listOf(300f, 600f), vertical)
        assertEquals(listOf(200f, 400f), horizontal)
    }

    @Test
    fun thirds_verticalLinesSpanFullHeight() {
        val segments = CameraGuides.thirds(width = 300f, height = 800f)

        segments.filter { it.startX == it.endX }.forEach { line ->
            assertEquals(0f, line.startY, 0f)
            assertEquals(800f, line.endY, 0f)
        }
    }

    @Test
    fun thirds_horizontalLinesSpanFullWidth() {
        val segments = CameraGuides.thirds(width = 300f, height = 800f)

        segments.filter { it.startY == it.endY }.forEach { line ->
            assertEquals(0f, line.startX, 0f)
            assertEquals(300f, line.endX, 0f)
        }
    }

    @Test
    fun thirds_zeroSizedViewport_hasNoLines() {
        assertTrue(CameraGuides.thirds(0f, 600f).isEmpty())
        assertTrue(CameraGuides.thirds(900f, 0f).isEmpty())
        assertTrue(CameraGuides.thirds(-1f, -1f).isEmpty())
    }
}
