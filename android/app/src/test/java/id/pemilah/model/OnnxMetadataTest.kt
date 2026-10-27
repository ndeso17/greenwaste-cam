package id.pemilah.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti aturan pembacaan metadata model ONNX (D-09, WP-02): label dari metadata
 * `names`, ukuran masukan dari bentuk tensor, dan keluarga keluaran dari bentuk
 * keluaran + jumlah label (MOD-01, MOD-02, SC-09).
 *
 * Seluruh nilai berasal dari bentuk/metadata model; tidak ada angka yang
 * diambil dari aplikasi.
 */
class OnnxMetadataTest {

    private val yolov8DetectionShape = listOf(1L, 3L, 640L, 640L)

    @Test
    fun parseClassNames_indexedObject_returnsOrderedLabels() {
        val labels = OnnxMetadata.parseClassNames(
            """{"0": "Organik", "1": "Plastik", "2": "Kaleng"}""",
        )

        assertEquals(listOf("Organik", "Plastik", "Kaleng"), labels)
    }

    @Test
    fun parseClassNames_objectWithShuffledKeys_followsClassIndex() {
        val labels = OnnxMetadata.parseClassNames(
            """{"2": "Kaleng", "0": "Organik", "1": "Plastik"}""",
        )

        assertEquals(listOf("Organik", "Plastik", "Kaleng"), labels)
    }

    @Test
    fun parseClassNames_array_returnsOrderedLabels() {
        assertEquals(
            listOf("Organik", "Plastik"),
            OnnxMetadata.parseClassNames("""["Organik", "Plastik"]"""),
        )
    }

    @Test
    fun parseClassNames_pythonReprWithSingleQuotes_returnsOrderedLabels() {
        // Ultralytics menulis metadata `names` memakai `repr` Python.
        val labels = OnnxMetadata.parseClassNames(
            "{0: 'Organik', 1: 'Plastik', 2: 'Kaleng', 3: 'Styrofoam', 4: 'Kaca', 5: 'Kardus', 6: 'Besi'}",
        )

        assertEquals(
            listOf("Organik", "Plastik", "Kaleng", "Styrofoam", "Kaca", "Kardus", "Besi"),
            labels,
        )
    }

    @Test
    fun parseClassNames_pythonReprWithDuplicateKeys_returnsNull() {
        assertNull(OnnxMetadata.parseClassNames("{0: 'Organik', 0: 'Plastik'}"))
    }

    @Test
    fun parseClassNames_missingOrBroken_returnsNull() {
        assertNull(OnnxMetadata.parseClassNames(null))
        assertNull(OnnxMetadata.parseClassNames("   "))
        assertNull(OnnxMetadata.parseClassNames("bukan json"))
        assertNull(OnnxMetadata.parseClassNames("{}"))
        // Indeks tidak lengkap: label kelas 1 hilang, sehingga urutan kanal
        // keluaran tidak dapat dipastikan.
        assertNull(OnnxMetadata.parseClassNames("""{"0": "Organik", "2": "Kaleng"}"""))
        // Kunci ganda menandakan metadata rusak.
        assertNull(OnnxMetadata.parseClassNames("""{"0": "Organik", "0": "Plastik"}"""))
        assertNull(OnnxMetadata.parseClassNames("""{"0": "Organik", "x": "Plastik"}"""))
    }

    @Test
    fun inputSpecOf_nchwShape_yieldsWidthHeightAndLayout() {
        val spec = OnnxMetadata.inputSpecOf(yolov8DetectionShape)

        assertEquals(InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW), spec)
    }

    @Test
    fun inputSpecOf_nhwcShape_yieldsWidthHeightAndLayout() {
        val spec = OnnxMetadata.inputSpecOf(listOf(1L, 640L, 640L, 3L))

        assertEquals(InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NHWC), spec)
    }

    @Test
    fun inputSpecOf_unsupportedShape_returnsNull() {
        assertNull(OnnxMetadata.inputSpecOf(null))
        assertNull(OnnxMetadata.inputSpecOf(listOf(1L, 640L, 640L)))
        // Kanel bukan tiga kanal warna, sehingga bukan masukan gambar.
        assertNull(OnnxMetadata.inputSpecOf(listOf(1L, 1L, 640L, 640L)))
        // Dimensi dinamis: ukuran pasti tidak diketahui.
        assertNull(OnnxMetadata.inputSpecOf(listOf(1L, 3L, -1L, -1L)))
    }

    @Test
    fun outputSpecOf_channelsFirstShape_isYolov8WithoutObjectness() {
        val spec = OnnxMetadata.outputSpecOf(listOf(1L, 7L, 8400L), classCount = 3)

        assertEquals(
            OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = false, hasObjectness = false),
            spec,
        )
    }

    @Test
    fun outputSpecOf_channelsLastShape_isYolov5WithObjectness() {
        val spec = OnnxMetadata.outputSpecOf(listOf(1L, 25200L, 8L), classCount = 3)

        assertEquals(
            OutputSpec(OutputFormat.YOLO_V5, boxesNormalized = false, hasObjectness = true),
            spec,
        )
    }

    @Test
    fun outputSpecOf_shapeNotMatchingLabelCount_returnsNull() {
        // 99 kanal tidak sama dengan 4 + 3 label maupun 5 + 3 label.
        assertNull(OnnxMetadata.outputSpecOf(listOf(1L, 99L, 8400L), classCount = 3))
        // Dua dimensi dengan jumlah kelas yang tidak cocok juga ditolak.
        assertNull(OnnxMetadata.outputSpecOf(listOf(1L, 8400L), classCount = 3))
        assertNull(OnnxMetadata.outputSpecOf(null, classCount = 3))
    }

    @Test
    fun outputSpecOf_classificationShape_isClassificationOnly() {
        // ResNet18 pengguna: `logits` [batch_size, 7] (D-10).
        val spec = OnnxMetadata.outputSpecOf(listOf(-1L, 7L), classCount = 7)

        assertEquals(
            OutputSpec(OutputFormat.CLASSIFICATION_ONLY, boxesNormalized = false, hasObjectness = false),
            spec,
        )
    }

    @Test
    fun outputSpecOf_withoutLabels_isRejected() {
        assertNull(OnnxMetadata.outputSpecOf(listOf(1L, 4L, 8400L), classCount = 0))
    }

    @Test
    fun inspectedModel_infoReportsClassCountFromMetadata() {
        val info = OnnxModelInfo(
            labels = listOf("Organik", "Plastik", "Kaleng"),
            input = requireNotNull(OnnxMetadata.inputSpecOf(yolov8DetectionShape)),
            output = requireNotNull(OnnxMetadata.outputSpecOf(listOf(1L, 7L, 8400L), 3)),
            modelVersion = "8.3.0",
        )

        assertEquals(3, info.classCount)
        assertTrue(info.output.format == OutputFormat.YOLO_V8)
    }
}