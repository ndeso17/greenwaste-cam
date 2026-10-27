package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import id.pemilah.storage.StoreProblemReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti repositori profil (WP-04): simpan→daftar→muat setara, profil rusak tetap
 * terlihat beserta alasan, dan profil aktif yang hilang menghasilkan keadaan
 * kosong, bukan galat (MOD-01, MOD-02, MOD-03, NFR-03).
 */
class ProfileRepositoryTest {

    private fun repository(): Pair<ConfigStore, ProfileRepository> {
        val store = ConfigStore(MemoryConfigStorage())
        return store to ProfileRepository(store)
    }

    private val validProfile = ModelProfile(
        schemaVersion = 1,
        profileId = "6f1a1d2c-0000-4000-8000-000000000001",
        name = "yolov8-sampah-v2",
        version = "2.0.0",
        modelFile = "profile-assets/yolov8-sampah-v2.onnx",
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
        val (_, repository) = repository()

        assertEquals(ProfileWriteResult.Written, repository.save(validProfile))

        val listed = repository.list()
        assertEquals(1, listed.size)
        val stored = listed.single()
        assertTrue("profil tersimpan harus dapat dipakai: $stored", stored is StoredProfile.Usable)
        assertEquals(validProfile, (stored as StoredProfile.Usable).profile)

        val found = repository.find(validProfile.profileId)
        assertTrue(found is StoredProfile.Usable)
        assertEquals(validProfile, (found as StoredProfile.Usable).profile)
    }

    @Test
    fun save_writesFileNamedAfterProfileId() {
        val (store, repository) = repository()

        repository.save(validProfile)

        assertTrue(
            "berkas harus memakai nama profile-<profileId>.json",
            store.profileFileExists("profile-${validProfile.profileId}.json"),
        )
    }

    @Test
    fun save_invalidProfile_isRejectedAndWritesNothing() {
        val (store, repository) = repository()
        val tooHigh = validProfile.copy(confidenceThreshold = 1.5)

        val result = repository.save(tooHigh)

        assertTrue("ambang tidak sah harus ditolak: $result", result is ProfileWriteResult.Rejected)
        assertTrue(
            (result as ProfileWriteResult.Rejected).problems.any {
                it.reason == ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD
            },
        )
        assertEquals(emptyList<String>(), store.listProfileFiles())
    }

    @Test
    fun list_brokenJsonFile_staysVisibleWithReason() {
        val (store, repository) = repository()
        store.writeProfileFile("profile-rusak.json", "{\"schemaVersion\": 1,")

        val listed = repository.list()

        assertEquals(1, listed.size)
        val stored = listed.single()
        assertTrue("profil rusak tidak boleh hilang: $stored", stored is StoredProfile.Broken)
        assertTrue((stored as StoredProfile.Broken).jsonProblems.isNotEmpty())
        // Tidak ada penghapusan otomatis berkas yang rusak.
        assertTrue(store.profileFileExists("profile-rusak.json"))
    }

    @Test
    fun list_fileFailingValidation_reportsValidatorProblems() {
        val (store, repository) = repository()
        val withoutLabels = ProfileJson.encode(
            validProfile.copy(labels = emptyList(), classMapping = emptyMap(), binAssignment = emptyMap()),
        )
        store.writeProfileFile("profile-tanpa-label.json", withoutLabels)

        val stored = repository.list().single()

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
        val (store, repository) = repository()
        repository.save(validProfile)
        store.writeProfileFile("profile-belum-selesai.json.tmp", "{\"separuh\":")

        assertEquals(
            listOf("profile-${validProfile.profileId}.json"),
            store.listProfileFiles(),
        )
    }

    @Test
    fun active_withoutSelection_isNoneNotSelected() {
        val (_, repository) = repository()

        assertEquals(
            ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED),
            repository.active(),
        )
    }

    @Test
    fun active_afterSetActive_returnsStoredProfile() {
        val (_, repository) = repository()
        repository.save(validProfile)

        assertEquals(ProfileWriteResult.Written, repository.setActive(validProfile.profileId))
        assertEquals(ActiveProfileState.Selected(validProfile), repository.active())
    }

    @Test
    fun active_whenProfileFileDeleted_isNoneFileMissing() {
        val (store, repository) = repository()
        repository.save(validProfile)
        repository.setActive(validProfile.profileId)
        // Penanda aktif diarahkan ke profil yang tidak pernah ada berkasnya.
        store.writeProfileFile("active-profile.txt", "profil-hantu")

        assertEquals(
            ActiveProfileState.None(ActiveProfileAbsentReason.FILE_MISSING, "profil-hantu"),
            repository.active(),
        )
    }

    @Test
    fun setActive_unknownProfile_isNotFound() {
        val (_, repository) = repository()

        assertEquals(ProfileWriteResult.NotFound("tidak-ada"), repository.setActive("tidak-ada"))
    }

    @Test
    fun setActive_brokenProfile_isUnusableAndLeavesMarkerUnchanged() {
        val (store, repository) = repository()
        repository.save(validProfile)
        repository.setActive(validProfile.profileId)
        store.writeProfileFile("profile-rusak.json", "bukan json")

        val result = repository.setActive("rusak")

        assertTrue("profil rusak tidak boleh diaktifkan: $result", result is ProfileWriteResult.Unusable)
        assertEquals(ActiveProfileState.Selected(validProfile), repository.active())
    }

    @Test
    fun save_storageFailure_reportedAsStorageFailed() {
        val memory = MemoryConfigStorage()
        val repository = ProfileRepository(ConfigStore(memory))
        memory.failWrite = true

        val result = repository.save(validProfile)

        assertEquals(
            ProfileWriteResult.StorageFailed(StoreProblemReason.WRITE_FAILED, "disk penuh"),
            result,
        )
    }
}