package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import id.pemilah.storage.StoreProblemReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti repositori profil (WP-04, WP-05): simpan→daftar→muat setara, salinan
 * berkas model tersimpan bersama profil, profil rusak tetap terlihat beserta
 * alasan, dan profil aktif yang hilang menghasilkan keadaan kosong, bukan galat
 * (MOD-01, MOD-02, MOD-03, NFR-03, D-09).
 */
class ProfileRepositoryTest {

    private class Fixture {
        val storage = MemoryConfigStorage()
        val store = ConfigStore(storage)
        val repository = ProfileRepository(store)
    }

    /** Isi model tiruan; yang diuji adalah keberadaan berkas, bukan isinya. */
    private val modelBytes = byteArrayOf(1, 2, 3)

    private val validProfile = ModelProfile(
        schemaVersion = 1,
        profileId = "6f1a1d2c-0000-4000-8000-000000000001",
        name = "yolov8-sampah-v2",
        version = "2.0.0",
        modelFile = "model-6f1a1d2c-0000-4000-8000-000000000001.onnx",
        labels = listOf("Organik", "Kertas_lembaran"),
        classMapping = mapOf("Organik" to WasteClass.ORGANIK, "Kertas_lembaran" to WasteClass.KARDUS),
        input = InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = true, hasObjectness = false),
        confidenceThreshold = 0.45,
        groupingEnabled = false,
        binAssignment = mapOf(WasteClass.ORGANIK to "B1", WasteClass.KARDUS to null),
    )

    @Test
    fun save_list_find_roundTripIsEquivalent() {
        val fixture = Fixture()

        assertEquals(
            ProfileWriteResult.Written,
            fixture.repository.saveWithModel(validProfile, modelBytes),
        )

        val listed = fixture.repository.list()
        assertEquals(1, listed.size)
        val stored = listed.single()
        assertTrue("profil tersimpan harus dapat dipakai: $stored", stored is StoredProfile.Usable)
        assertEquals(validProfile, (stored as StoredProfile.Usable).profile)

        val found = fixture.repository.find(validProfile.profileId)
        assertTrue(found is StoredProfile.Usable)
        assertEquals(validProfile, (found as StoredProfile.Usable).profile)
    }

    @Test
    fun saveWithModel_writesModelCopyAndProfileFile() {
        val fixture = Fixture()

        fixture.repository.saveWithModel(validProfile, modelBytes)

        assertTrue(
            "berkas model harus disalin ke penyimpanan aplikasi",
            fixture.store.modelFileExists(validProfile.modelFile),
        )
        assertEquals(
            "isi berkas model harus sama dengan yang diimpor",
            modelBytes.toList(),
            fixture.store.readModelFile(validProfile.modelFile)?.toList(),
        )
        assertTrue(
            "berkas profil harus memakai nama profile-<profileId>.json",
            fixture.store.profileFileExists("profile-${validProfile.profileId}.json"),
        )
    }

    @Test
    fun saveWithModel_invalidProfile_writesNeitherFile() {
        val fixture = Fixture()
        val tooHigh = validProfile.copy(confidenceThreshold = 1.5)

        val result = fixture.repository.saveWithModel(tooHigh, modelBytes)

        assertTrue("profil tidak sah harus ditolak: $result", result is ProfileWriteResult.Rejected)
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
        assertFalse(
            "model tidak boleh ditulis untuk profil yang ditolak",
            fixture.store.modelFileExists(validProfile.modelFile),
        )
    }

    @Test
    fun saveWithModel_storageFailureOnModelFile_reportsStorageFailed() {
        val fixture = Fixture()
        fixture.storage.failWrite = true

        val result = fixture.repository.saveWithModel(validProfile, modelBytes)

        assertTrue(
            "kegagalan tulis harus dilaporkan: $result",
            result is ProfileWriteResult.StorageFailed,
        )
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun find_profileWhoseModelFileIsGone_isBrokenWithModelFileMissing() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)
        // Berkas model hilang, misalnya penyimpanan dibersihkan operator.
        fixture.storage.binaryFiles.remove(validProfile.modelFile)

        val stored = fixture.repository.find(validProfile.profileId)

        assertTrue("profil tanpa model tidak boleh dipakai: $stored", stored is StoredProfile.Broken)
        assertTrue(
            "alasan berkas model hilang harus dibawa: $stored",
            (stored as StoredProfile.Broken).validationProblems.any {
                it.reason == ProfileProblemReason.MODEL_FILE_MISSING
            },
        )
    }

    @Test
    fun active_profileWhoseModelFileIsGone_isNoneNotValid() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)
        fixture.repository.setActive(validProfile.profileId)
        fixture.storage.binaryFiles.remove(validProfile.modelFile)

        assertEquals(
            ActiveProfileState.None(ActiveProfileAbsentReason.NOT_VALID, validProfile.profileId),
            fixture.repository.active(),
        )
    }

    @Test
    fun exists_isTrueForStoredProfileAndFalseOtherwise() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)

        assertTrue(fixture.repository.exists(validProfile.profileId))
        assertFalse("id yang belum dipakai bukan profil tersimpan", fixture.repository.exists("id-lain"))
    }

    @Test
    fun save_invalidProfile_isRejectedAndWritesNothing() {
        val fixture = Fixture()
        val tooHigh = validProfile.copy(confidenceThreshold = 1.5)

        val result = fixture.repository.save(tooHigh)

        assertTrue("ambang tidak sah harus ditolak: $result", result is ProfileWriteResult.Rejected)
        assertTrue(
            (result as ProfileWriteResult.Rejected).problems.any {
                it.reason == ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD
            },
        )
        assertEquals(emptyList<String>(), fixture.store.listProfileFiles())
    }

    @Test
    fun list_brokenJsonFile_staysVisibleWithReason() {
        val fixture = Fixture()
        fixture.store.writeProfileFile("profile-rusak.json", "{\"schemaVersion\": 1,")

        val listed = fixture.repository.list()

        assertEquals(1, listed.size)
        val stored = listed.single()
        assertTrue("profil rusak tidak boleh hilang: $stored", stored is StoredProfile.Broken)
        assertTrue((stored as StoredProfile.Broken).jsonProblems.isNotEmpty())
        // Tidak ada penghapusan otomatis berkas yang rusak.
        assertTrue(fixture.store.profileFileExists("profile-rusak.json"))
    }

    @Test
    fun list_fileFailingValidation_reportsValidatorProblems() {
        val fixture = Fixture()
        val withoutLabels = ProfileJson.encode(
            validProfile.copy(labels = emptyList(), classMapping = emptyMap(), binAssignment = emptyMap()),
        )
        fixture.store.writeProfileFile("profile-tanpa-label.json", withoutLabels)

        val stored = fixture.repository.list().single()

        assertTrue(stored is StoredProfile.Broken)
        assertTrue(
            "alasan validator harus dibawa: $stored",
            (stored as StoredProfile.Broken).validationProblems.any {
                it.reason == ProfileProblemReason.NO_LABELS
            },
        )
    }

    @Test
    fun list_ignoresTemporaryFilesAndForeignNames() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)
        fixture.store.writeProfileFile("profile-belum-selesai.json.tmp", "{\"separuh\":")

        assertEquals(
            listOf("profile-${validProfile.profileId}.json"),
            fixture.store.listProfileFiles(),
        )
    }

    @Test
    fun active_withoutSelection_isNoneNotSelected() {
        val fixture = Fixture()

        assertEquals(
            ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED),
            fixture.repository.active(),
        )
    }

    @Test
    fun active_afterSetActive_returnsStoredProfile() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)

        assertEquals(
            ProfileWriteResult.Written,
            fixture.repository.setActive(validProfile.profileId),
        )
        assertEquals(ActiveProfileState.Selected(validProfile), fixture.repository.active())
    }

    @Test
    fun active_whenProfileFileDeleted_isNoneFileMissing() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)
        fixture.repository.setActive(validProfile.profileId)
        // Penanda aktif diarahkan ke profil yang tidak pernah ada berkasnya.
        fixture.store.writeProfileFile("active-profile.txt", "profil-hantu")

        assertEquals(
            ActiveProfileState.None(ActiveProfileAbsentReason.FILE_MISSING, "profil-hantu"),
            fixture.repository.active(),
        )
    }

    @Test
    fun setActive_unknownProfile_isNotFound() {
        val fixture = Fixture()

        assertEquals(
            ProfileWriteResult.NotFound("tidak-ada"),
            fixture.repository.setActive("tidak-ada"),
        )
    }

    @Test
    fun setActive_brokenProfile_isUnusableAndLeavesMarkerUnchanged() {
        val fixture = Fixture()
        fixture.repository.saveWithModel(validProfile, modelBytes)
        fixture.repository.setActive(validProfile.profileId)
        fixture.store.writeProfileFile("profile-rusak.json", "bukan json")

        val result = fixture.repository.setActive("rusak")

        assertTrue("profil rusak tidak boleh diaktifkan: $result", result is ProfileWriteResult.Unusable)
        assertEquals(ActiveProfileState.Selected(validProfile), fixture.repository.active())
    }

    @Test
    fun save_storageFailure_reportedAsStorageFailed() {
        val fixture = Fixture()
        fixture.storage.failWrite = true

        val result = fixture.repository.save(validProfile)

        assertEquals(
            ProfileWriteResult.StorageFailed(StoreProblemReason.WRITE_FAILED, "disk penuh"),
            result,
        )
    }
}
