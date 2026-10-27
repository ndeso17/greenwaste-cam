package id.pemilah.inference

import id.pemilah.model.InputSpec
import id.pemilah.model.TensorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti praproses klasifikasi (D-12): potong tengah, ukuran tensor, urutan
 * layout, normalisasi, dan softmax. Semua diuji tanpa Android dan tanpa ONNX
 * Runtime.
 */
class FramePreprocessTest {

    private val input = InputSpec(
        width = 4,
        height = 4,
        normalization = id.pemilah.model.Normalization.SCALE_0_1,
        layout = TensorLayout.NCHW,
    )

    /** Frame 2x1: piksel kiri merah, piksel kanan biru. */
    private fun twoPixelFrame() = ArgbFrame(
        pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt()),
        width = 2,
        height = 1,
    )

    @Test
    fun toTensor_sizeIsThreeTimesInputArea() {
        val frame = ArgbFrame(IntArray(64) { 0xFF808080.toInt() }, width = 8, height = 8)

        val tensor = FramePreprocess.toTensor(frame, input)

        assertEquals(3 * 4 * 4, tensor.size)
    }

    @Test
    fun toTensor_nchw_groupsChannelsFirst() {
        val frame = ArgbFrame(IntArray(4) { 0xFF808080.toInt() }, width = 2, height = 2)

        val tensor = FramePreprocess.toTensor(
            frame = frame,
            input = input,
            mean = floatArrayOf(0f, 0f, 0f),
            std = floatArrayOf(1f, 1f, 1f),
        )

        // Semua kanal berisi nilai sama (128/255) sehingga blok kanal identik.
        val plane = 4 * 4
        assertEquals(tensor[0], tensor[plane], 0.0001f)
        assertEquals(tensor[plane], tensor[2 * plane], 0.0001f)
        assertEquals(128f / 255f, tensor[0], 0.0001f)
    }

    @Test
    fun toTensor_nhwc_interleavesChannels() {
        val frame = ArgbFrame(IntArray(4) { 0xFF804020.toInt() }, width = 2, height = 2)

        val tensor = FramePreprocess.toTensor(
            frame = frame,
            input = input.copy(layout = TensorLayout.NHWC),
            mean = floatArrayOf(0f, 0f, 0f),
            std = floatArrayOf(1f, 1f, 1f),
        )

        assertEquals(0x80 / 255f, tensor[0], 0.0001f)
        assertEquals(0x40 / 255f, tensor[1], 0.0001f)
        assertEquals(0x20 / 255f, tensor[2], 0.0001f)
        assertEquals(tensor[0], tensor[3], 0.0001f)
    }

    @Test
    fun toTensor_appliesMeanAndStd() {
        val frame = ArgbFrame(IntArray(4) { 0xFFFFFFFF.toInt() }, width = 2, height = 2)

        val tensor = FramePreprocess.toTensor(frame, input)

        // Putih: (1 - 0.485) / 0.229 untuk kanal merah.
        assertEquals((1f - 0.485f) / 0.229f, tensor[0], 0.0001f)
    }

    @Test
    fun toTensor_centerCropsWideFrame() {
        // Frame 3x1: merah | hijau | biru. Sisi terpendek 1, potong tengah
        // mengambil piksel tengah (hijau), bukan piksel paling kiri.
        val frame = ArgbFrame(
            pixels = intArrayOf(
                0xFFFF0000.toInt(),
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt(),
            ),
            width = 3,
            height = 1,
        )

        val tensor = FramePreprocess.toTensor(
            frame = frame,
            input = input.copy(width = 1, height = 1),
            mean = floatArrayOf(0f, 0f, 0f),
            std = floatArrayOf(1f, 1f, 1f),
        )

        assertEquals(0f, tensor[0], 0.0001f)
        assertEquals(1f, tensor[1], 0.0001f)
        assertEquals(0f, tensor[2], 0.0001f)
    }

    @Test
    fun toTensor_rejectsMismatchedPixelArray() {
        val error = runCatching {
            ArgbFrame(pixels = IntArray(3), width = 2, height = 2)
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun softmax_sumsToOne() {
        val result = Classification.fromLogits(
            labels = listOf("Organik", "Plastik", "Kaca"),
            logits = floatArrayOf(1f, 2f, 3f),
        )

        assertEquals(1f, result.probabilities.sum(), 0.0001f)
        assertEquals(2, result.topIndex)
        assertEquals("Kaca", result.topLabel)
    }

    @Test
    fun softmax_largeLogitsDoNotOverflow() {
        val result = Classification.fromLogits(
            labels = listOf("A", "B"),
            logits = floatArrayOf(1000f, 1001f),
        )

        assertTrue(result.probabilities.all { it.isFinite() })
        assertEquals(1f, result.probabilities.sum(), 0.0001f)
        assertEquals("B", result.topLabel)
    }

    @Test
    fun ranked_ordersDescendingAndLimits() {
        val result = Classification.fromLogits(
            labels = listOf("A", "B", "C"),
            logits = floatArrayOf(0.1f, 5f, 1f),
        )

        val ranked = result.ranked(2)

        assertEquals(listOf("B", "C"), ranked.map { it.first })
        assertTrue(ranked[0].second >= ranked[1].second)
    }

    @Test
    fun emptyLogits_haveNoTopLabel() {
        val result = Classification.fromLogits(labels = emptyList(), logits = FloatArray(0))

        assertEquals(-1, result.topIndex)
        assertEquals(null, result.topLabel)
        assertTrue(result.ranked(3).isEmpty())
    }
}
