package id.pemilah.model

import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jalur impor berkas model ONNX (WP-05, D-09): berkas bukan ONNX, berkas tidak
 * terbaca, berkas kosong, model tanpa metadata kelas, bentuk tensor tidak
 * didukung, label tak dikenal, dan model sah. Setiap jalur gagal wajib
 * meninggalkan daftar profil apa adanya (MOD-01, MOD-02, SC-09).
 */
class ModelImportTest {

    private class Fixture {
        val storage = MemoryConfigStorage()
        val store = ConfigStore(storage)
        val repository = ProfileRepository(store)
        val inspector = FakeInspector()
        val import = ModelImport(repository, inspector)
    }

    /** Model tiruan yang menulis tujuh kelas resmi pada metadatanya. */
    private val sevenClasses = listOf(
        "Organik",
        "Plastik",
        "Kaleng",
        "Styrofoam",
        "Kaca",
        "Kardus",
        "Besi",
    )

    private val onnxBytes = byteArrayOf(0x08, 0x07, 0x12, 0x03)

    private class FakeReader(
        private val name: String?,
        private val content: ByteArray?,
    ) : ModelFileReader {
        var lastUri: String? = null

        override fun displayName(uri: String): String? {
            lastUri = uri
            return name
        }

        override fun readBytes(uri: String): ByteArray? = content
    }

    /** Inspektor tiruan: mengembalikan hasil yang ditentukan test. */
    private class FakeInspector : OnnxModelInspector {
        var result: OnnxInspectionResult = OnnxInspectionResult.Rejected(
            OnnxInspectionProblem(OnnxInspectionCode.UNREADABLE_MODEL),
        )
        var inspectedBytes: ByteArray? = null

        override fun inspect(bytes: ByteArray): OnnxInspectionResult {
            inspectedBytes = bytes
            return result
        }

        fun accepts(labels: List<String>, version: String? = "8.3.0") {
            result = OnnxInspectionResult.Inspected(
                OnnxModelInfo(
                    labels = labels,
                    input = requireNotNull(OnnxMetadata.inputSpecOf(listOf(1L, 3L, 640L, 640L))),
                    output = requireNotNull(
                        OnnxMetadata.outputSpecOf(listOf(1L, (4 + labels.size).toLong(), 8400L), labels.size),
                    ),
                    modelVersion = version,
                ),
            )
        }
    }

    private fun codes(result: ModelImportResult): List<ModelImportProblemCode> {
        assertTrue("impor harus ditolak: $result", result is ModelImportResult.Rejected)
        return (result as ModelImportResult.Rejected).problems.map { it.code }
    }

    @Test
    fun onnxModel_isImportedAndStored() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import("content://model.onnx", FakeReader("sampah-v3.onnx", onnxBytes))

        assertTrue("model sah harus diimpor: $result", result is ModelImportResult.Imported)
        val imported = (result as ModelImportResult.Imported).profile
        assertEquals("sampah-v3", imported.name)
        assertEquals("8.3.0", imported.version)
        assertEquals(sevenClasses, imported.labels)
        assertEquals(7, imported.labels.size)
        // Ambang belum ditetapkan; aplikasi tidak mengarang ambang operator.
        assertEquals(0.0, imported.confidenceThreshold, 0.0)
        assertEquals(onnxBytes.size.toLong(), result.sizeBytes)
        assertTrue(
            "berkas model harus tersimpan di penyimpanan aplikasi",
            fixture.store.modelFileExists(imported.modelFile),
        )
        assertTrue(fixture.repository.list().single() is StoredProfile.Usable)
    }

    @Test
    fun classMapping_matchesModelLabelNamesToOfficialClasses() {
        val fixture = Fixture()
        fixture.inspector.accepts(listOf("organik", "PLASTIK", "Besi"))

        val imported = (
            fixture.import.import("content://m.onnx", FakeReader("m.onnx", onnxBytes))
                as ModelImportResult.Imported
            ).profile

        assertEquals(
            mapOf(
                "organik" to id.pemilah.domain.waste.WasteClass.ORGANIK,
                "PLASTIK" to id.pemilah.domain.waste.WasteClass.PLASTIK,
                "Besi" to id.pemilah.domain.waste.WasteClass.BESI,
            ),
            imported.classMapping,
        )
    }

    @Test
    fun unknownLabel_isRejectedAsInvalidProfile() {
        val fixture = Fixture()
        // Model 13 kelas (mis. `best.onnx`) tidak memuat tujuh kelas resmi.
        fixture.inspector.accepts(listOf("Organik", "Kertas_lembaran", "Logam_metal"))

        val result = fixture.import.import("content://b.onnx", FakeReader("best.onnx", onnxBytes))

        val problems = (result as ModelImportResult.Rejected).problems
        assertEquals(
            "setiap label tak dikenal dilaporkan sebagai profile ditolak",
            setOf(ModelImportProblemCode.INVALID_PROFILE),
            problems.map { it.code }.toSet(),
        )
        assertEquals(
            "label yang tidak dikenali harus disebut satu per satu",
            setOf(
                "classMapping.Kertas_lembaran",
                "classMapping.Logam_metal",
                // `Logam_metal` juga menggabungkan kaleng dan besi (Rules §2.6).
                "labels.Logam_metal",
            ),
            problems.mapNotNull { it.field }.toSet(),
        )
        assertEquals("tidak ada model yang boleh tersimpan", emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun jsonConfigFile_isRejectedAsNotOnnx() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import(
            "content://profil.json",
            FakeReader("profil.json", """{"schemaVersion": 1}""".toByteArray()),
        )

        assertEquals(listOf(ModelImportProblemCode.NOT_ONNX_MODEL), codes(result))
        assertEquals("berkas JSON tidak boleh dibaca sebagai model", null, fixture.inspector.inspectedBytes)
    }

    @Test
    fun missingDisplayName_isRejected() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import("content://x", FakeReader(null, onnxBytes))

        assertEquals(listOf(ModelImportProblemCode.MISSING_FILE_NAME), codes(result))
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun unreadableFile_isRejected() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import("content://x.onnx", FakeReader("x.onnx", null))

        assertEquals(listOf(ModelImportProblemCode.UNREADABLE_FILE), codes(result))
    }

    @Test
    fun emptyFile_isRejected() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import(
            "content://x.onnx",
            FakeReader("x.onnx", ByteArray(0)),
        )

        assertEquals(listOf(ModelImportProblemCode.EMPTY_FILE), codes(result))
    }

    @Test
    fun fileAboveConfiguredLimit_isRejected() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)
        val limited = ModelImport(fixture.repository, fixture.inspector, maxBytes = 2)

        val result = limited.import("content://x.onnx", FakeReader("x.onnx", onnxBytes))

        assertEquals(listOf(ModelImportProblemCode.FILE_TOO_LARGE), codes(result))
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun inspectionRejections_mapToImportCodes() {
        val mapping = mapOf(
            OnnxInspectionCode.UNREADABLE_MODEL to ModelImportProblemCode.UNREADABLE_MODEL,
            OnnxInspectionCode.MISSING_CLASS_NAMES to ModelImportProblemCode.MISSING_CLASS_NAMES,
            OnnxInspectionCode.UNSUPPORTED_INPUT_SHAPE to ModelImportProblemCode.UNSUPPORTED_INPUT_SHAPE,
            OnnxInspectionCode.UNSUPPORTED_OUTPUT_SHAPE to ModelImportProblemCode.UNSUPPORTED_OUTPUT_SHAPE,
        )
        mapping.forEach { (inspection, expected) ->
            val fixture = Fixture()
            fixture.inspector.result =
                OnnxInspectionResult.Rejected(OnnxInspectionProblem(inspection, "rincian"))

            val result = fixture.import.import("content://x.onnx", FakeReader("x.onnx", onnxBytes))

            assertEquals("kode $inspection harus dipetakan ke $expected", listOf(expected), codes(result))
            assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
        }
    }

    @Test
    fun secondImportWithSameFileName_keepsBothProfiles() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val first = fixture.import.import("content://a.onnx", FakeReader("sampah.onnx", onnxBytes))
            as ModelImportResult.Imported
        val second = fixture.import.import("content://b.onnx", FakeReader("sampah.onnx", onnxBytes))
            as ModelImportResult.Imported

        assertTrue("impor kedua tidak boleh menimpa profil lama", first.profile.profileId != second.profile.profileId)
        assertEquals(2, fixture.repository.list().size)
        assertEquals(2, fixture.store.listModelFiles().size)
    }

    @Test
    fun storageFailure_isReportedAndNoProfileStored() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)
        fixture.storage.failWrite = true

        val result = fixture.import.import("content://x.onnx", FakeReader("x.onnx", onnxBytes))

        assertEquals(listOf(ModelImportProblemCode.STORAGE_FAILED), codes(result))
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun readerReceivesTheSelectedUri() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)
        val reader = FakeReader("x.onnx", onnxBytes)

        fixture.import.import("content://pilihan.onnx", reader)

        assertEquals("content://pilihan.onnx", reader.lastUri)
    }

    @Test
    fun uppercaseExtension_isAccepted() {
        val fixture = Fixture()
        fixture.inspector.accepts(sevenClasses)

        val result = fixture.import.import("content://X.ONNX", FakeReader("X.ONNX", onnxBytes))

        assertTrue("akhiran .ONNX adalah berkas ONNX: $result", result is ModelImportResult.Imported)
    }
}
