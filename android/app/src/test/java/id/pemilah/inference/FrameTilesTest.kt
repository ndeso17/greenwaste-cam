package id.pemilah.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti pemotongan frame menjadi petak dan penyaringan kotak overlay (D-14).
 *
 * Kotak berasal dari batas petak yang lolos ambang keyakinan, bukan dari model
 * deteksi; batas itu diuji di sini supaya overlay tidak menggambar kotak yang
 * tidak dapat dipertanggungjawabkan.
 */
class FrameTilesTest {

    private fun frame(width: Int, height: Int) = ArgbFrame(
        pixels = IntArray(width * height) { 0xFF336699.toInt() },
        width = width,
        height = height,
    )

    @Test
    fun split_threeByThree_coversWholeFrameWithoutGaps() {
        val tiles = FrameTiles.split(frame(30, 30), columns = 3, rows = 3)

        assertEquals(9, tiles.size)
        // Baris pertama: 0..1/3, 1/3..2/3, 2/3..1.
        assertEquals(0f, tiles[0].region.left, 0.0001f)
        assertEquals(1f / 3f, tiles[0].region.right, 0.0001f)
        assertEquals(1f / 3f, tiles[1].region.left, 0.0001f)
        assertEquals(1f, tiles[2].region.right, 0.0001f)
        assertEquals(1f, tiles[8].region.bottom, 0.0001f)
    }

    @Test
    fun split_tileSizeMatchesRegion() {
        val tiles = FrameTiles.split(frame(30, 21), columns = 3, rows = 3)

        assertEquals(10, tiles[0].frame.width)
        assertEquals(7, tiles[0].frame.height)
        // Petak terakhir menyerap sisa pembagian tinggi (21 = 7+7+7).
        assertEquals(7, tiles[8].frame.height)
        assertEquals(10, tiles[2].frame.width)
    }

    @Test
    fun split_cropsCorrectPixels() {
        // Frame 3x1: merah | hijau | biru.
        val source = ArgbFrame(
            pixels = intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
            ),
            width = 3,
            height = 1,
        )

        val tiles = FrameTiles.split(source, columns = 3, rows = 1)

        assertEquals(0xFFFF0000.toInt(), tiles[0].frame.pixels.single())
        assertEquals(0xFF00FF00.toInt(), tiles[1].frame.pixels.single())
        assertEquals(0xFF0000FF.toInt(), tiles[2].frame.pixels.single())
    }

    @Test
    fun split_frameSmallerThanGrid_hasNoTiles() {
        assertTrue(FrameTiles.split(frame(2, 2), columns = 3, rows = 3).isEmpty())
    }

    @Test
    fun select_keepsOnlyTilesAboveThreshold() {
        val tiles = FrameTiles.split(frame(30, 30), columns = 3, rows = 3)
        val outcomes = listOf(
            ClassifierOutcome.Classified(classification("Kaca" to 0.9f)),
            ClassifierOutcome.Classified(classification("Organik" to 0.5f)),
            ClassifierOutcome.Failed(ClassifierFailure(ClassifierFailureCode.RUN_FAILED)),
        )

        val detections = TileDetectionFilter.select(tiles, outcomes, threshold = 0.6)

        assertEquals(1, detections.size)
        assertEquals("Kaca", detections.single().label)
    }

    @Test
    fun select_ordersByConfidenceAndLimits() {
        val tiles = FrameTiles.split(frame(30, 30), columns = 3, rows = 3)
        val outcomes = List(9) { index ->
            ClassifierOutcome.Classified(
                classification("Kaca" to (0.7f + index * 0.01f)),
            )
        }

        val detections = TileDetectionFilter.select(tiles, outcomes, threshold = 0.6, limit = 4)

        assertEquals(4, detections.size)
        assertEquals(0.78f, detections.first().probability, 0.0001f)
        assertTrue(detections.zipWithNext().all { (a, b) -> a.probability >= b.probability })
    }

    @Test
    fun select_withoutThreshold_returnsNothing() {
        val tiles = FrameTiles.split(frame(30, 30), columns = 3, rows = 3)
        val outcomes = List(9) {
            ClassifierOutcome.Classified(classification("Kaca" to 0.99f))
        }

        assertTrue(TileDetectionFilter.select(tiles, outcomes, threshold = 0.0).isEmpty())
    }

    @Test
    fun select_missingOutcome_isSkipped() {
        val tiles = FrameTiles.split(frame(30, 30), columns = 3, rows = 3)
        val outcomes = listOf(ClassifierOutcome.Classified(classification("Kaca" to 0.9f)))

        val detections = TileDetectionFilter.select(tiles, outcomes, threshold = 0.6)

        assertEquals(1, detections.size)
    }

    private fun classification(vararg pairs: Pair<String, Float>): Classification {
        val labels = listOf("Organik", "Plastik", "Kaleng", "Styrofoam", "Kaca", "Kardus", "Besi")
        val probabilities = FloatArray(labels.size) { 0.01f }
        pairs.forEach { (label, probability) -> probabilities[labels.indexOf(label)] = probability }
        return Classification(labels, probabilities)
    }
}
