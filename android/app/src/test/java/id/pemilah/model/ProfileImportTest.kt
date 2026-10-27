package id.pemilah.model

import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jalur impor berkas profil (WP-08): berkas tidak terbaca, berkas kosong, JSON
 * rusak, profil tidak valid, dan profil valid. Setiap jalur gagal wajib
 * meninggalkan daftar profil apa adanya (MOD-01, MOD-02, SC-09).
 */
class ProfileImportTest {

    private val validJson = """
        {
          "schemaVersion": 1,
          "profileId": "6f1a1d2c-0000-4000-8000-000000000001",
          "name": "yolov8-sampah-v2",
          "version": "2.0.0",
          "modelFile": "profile-assets/yolov8-sampah-v2.onnx",
          "labels": ["Organik"],
          "classMapping": { "Organik": "ORGANIK" },
          "input": {
            "width": 640, "height": 640,
            "normalization": "scale_0_1", "layout": "nchw"
          },
          "output": {
            "format": "yolo_v8", "boxesNormalized": true, "hasObjectness": false
          },
          "confidenceThreshold": 0.45,
          "groupingEnabled": false,
          "binAssignment": { "ORGANIK": "B1" }
        }
    """.trimIndent()

    private class FakeReader(private val content: String?) : ProfileFileReader {
        var lastUri: String? = null

        override fun read(uri: String): String? {
            lastUri = uri
            return content
        }
    }

    private fun repository(): Pair<MemoryConfigStorage, ProfileRepository> {
        val storage = MemoryConfigStorage()
        return storage to ProfileRepository(ConfigStore(storage))
    }

    private fun codes(result: ProfileImportResult): List<ProfileImportProblemCode> {
        assertTrue("impor harus ditolak: $result", result is ProfileImportResult.Rejected)
        return (result as ProfileImportResult.Rejected).problems.map { it.code }
    }

    @Test
    fun validFile_isImportedAndStored() {
        val (_, repository) = repository()
        val import = ProfileImport(repository)

        val result = import.import("content://profil.json", FakeReader(validJson))

        assertTrue("berkas sah harus diimpor: $result", result is ProfileImportResult.Imported)
        val imported = (result as ProfileImportResult.Imported)
        assertEquals("yolov8-sampah-v2", imported.profile.name)
        assertEquals(validJson.toByteArray(Charsets.UTF_8).size.toLong(), imported.sizeBytes)
        assertTrue(repository.list().single() is StoredProfile.Usable)
    }

    @Test
    fun unreadableFile_isRejectedAndListUnchanged() {
        val (storage, repository) = repository()
        ProfileImport(repository).import("content://lama.json", FakeReader(validJson))
        val before = storage.files.toMap()

        val result = ProfileImport(repository).import("content://hilang.json", FakeReader(null))

        assertEquals(listOf(ProfileImportProblemCode.UNREADABLE_FILE), codes(result))
        assertEquals(before, storage.files)
    }

    @Test
    fun emptyFile_isRejected() {
        val (_, repository) = repository()

        val result = ProfileImport(repository).import("content://kosong.json", FakeReader("   \n"))

        assertEquals(listOf(ProfileImportProblemCode.EMPTY_FILE), codes(result))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun brokenJson_isRejectedWithFieldAndListUnchanged() {
        val (storage, repository) = repository()
        ProfileImport(repository).import("content://lama.json", FakeReader(validJson))
        val before = storage.files.toMap()

        val result = ProfileImport(repository).import(
            "content://rusak.json",
            FakeReader(validJson.replace("\"name\": \"yolov8-sampah-v2\",", "")),
        )

        assertEquals(listOf(ProfileImportProblemCode.INVALID_JSON), codes(result))
        val problem = (result as ProfileImportResult.Rejected).problems.single()
        assertEquals("name", problem.field)
        assertEquals(before, storage.files)
    }

    @Test
    fun invalidProfile_isRejectedByValidatorAndListUnchanged() {
        val (storage, repository) = repository()
        ProfileImport(repository).import("content://lama.json", FakeReader(validJson))
        val before = storage.files.toMap()

        val result = ProfileImport(repository).import(
            "content://ambang.json",
            FakeReader(validJson.replace("\"confidenceThreshold\": 0.45", "\"confidenceThreshold\": 0")),
        )

        assertEquals(listOf(ProfileImportProblemCode.INVALID_PROFILE), codes(result))
        val problem = (result as ProfileImportResult.Rejected).problems.single()
        assertEquals("confidenceThreshold", problem.field)
        assertEquals(before, storage.files)
    }

    @Test
    fun fileAboveConfiguredLimit_isRejected() {
        val (_, repository) = repository()
        val import = ProfileImport(repository, maxBytes = 32)

        val result = import.import("content://besar.json", FakeReader(validJson))

        assertEquals(listOf(ProfileImportProblemCode.FILE_TOO_LARGE), codes(result))
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun withoutConfiguredLimit_sizeIsNotChecked() {
        // Batas ukuran berasal dari konfigurasi yang belum ditetapkan (B-06);
        // tanpa nilai batas, impor tidak boleh mengarang ambang sendiri.
        val (_, repository) = repository()

        val result = ProfileImport(repository, maxBytes = null)
            .import("content://bebas.json", FakeReader(validJson))

        assertTrue(result is ProfileImportResult.Imported)
    }

    @Test
    fun reimportingSameProfileId_replacesSingleFile() {
        val (storage, repository) = repository()
        val import = ProfileImport(repository)

        import.import("content://a.json", FakeReader(validJson))
        import.import(
            "content://b.json",
            FakeReader(validJson.replace("\"name\": \"yolov8-sampah-v2\"", "\"name\": \"versi-baru\"")),
        )

        assertEquals(1, repository.list().size)
        val stored = repository.list().single() as StoredProfile.Usable
        assertEquals("versi-baru", stored.profile.name)
        assertEquals(1, storage.files.count { it.key.startsWith("profile-") })
    }

    @Test
    fun storageFailure_isReportedAndPreviousProfileIntact() {
        val storage = MemoryConfigStorage()
        val repository = ProfileRepository(ConfigStore(storage))
        ProfileImport(repository).import("content://lama.json", FakeReader(validJson))
        val before = storage.files.toMap()
        storage.failWrite = true

        val result = ProfileImport(repository).import("content://baru.json", FakeReader(validJson))

        assertEquals(listOf(ProfileImportProblemCode.STORAGE_FAILED), codes(result))
        assertEquals(before, storage.files)
    }

    @Test
    fun readerReceivesTheSelectedUri() {
        val (_, repository) = repository()
        val reader = FakeReader(validJson)

        ProfileImport(repository).import("content://pilihan.json", reader)

        assertEquals("content://pilihan.json", reader.lastUri)
    }
}