package id.pemilah.ui

import id.pemilah.domain.operation.MachineState
import id.pemilah.domain.waste.WasteClass
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.InputSpec
import id.pemilah.model.ModelFileReader
import id.pemilah.model.ModelImport
import id.pemilah.model.ModelProfile
import id.pemilah.model.Normalization
import id.pemilah.model.OnnxInspectionCode
import id.pemilah.model.OnnxInspectionProblem
import id.pemilah.model.OnnxInspectionResult
import id.pemilah.model.OnnxMetadata
import id.pemilah.model.OnnxModelInfo
import id.pemilah.model.OnnxModelInspector
import id.pemilah.model.OutputFormat
import id.pemilah.model.OutputSpec
import id.pemilah.model.ProfileRepository
import id.pemilah.model.ProfileSwitchBlockCode
import id.pemilah.model.StoredProfile
import id.pemilah.model.TensorLayout
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sesi layar Models (WP-09, D-09): impor model ONNX, pemilihan model aktif,
 * penolakan saat berjalan atau saat ambang belum ditetapkan, model aktif
 * bertahan setelah sesi dibangun ulang, dan label untuk layar Operasi (MOD-01,
 * MOD-03, MOD-04, NFR-03, UI-01).
 */
class ModelsSessionTest {

    private val sevenClasses = listOf(
        "Organik",
        "Plastik",
        "Kaleng",
        "Styrofoam",
        "Kaca",
        "Kardus",
        "Besi",
    )

    private val onnxBytes = byteArrayOf(0x08, 0x07)

    private class FakeReader(private val name: String?, private val bytes: ByteArray?) :
        ModelFileReader {
        override fun displayName(uri: String): String? = name

        override fun readBytes(uri: String): ByteArray? = bytes
    }

    private class FakeInspector(private val result: OnnxInspectionResult) : OnnxModelInspector {
        override fun inspect(bytes: ByteArray): OnnxInspectionResult = result
    }

    private fun accepts(labels: List<String>) = OnnxInspectionResult.Inspected(
        OnnxModelInfo(
            labels = labels,
            input = requireNotNull(OnnxMetadata.inputSpecOf(listOf(1L, 3L, 640L, 640L))),
            output = requireNotNull(
                OnnxMetadata.outputSpecOf(listOf(1L, (4 + labels.size).toLong(), 8400L), labels.size),
            ),
            modelVersion = "8.3.0",
        ),
    )

    private fun session(
        storage: MemoryConfigStorage,
        inspector: FakeInspector = FakeInspector(accepts(sevenClasses)),
    ): ModelsSession {
        val repository = ProfileRepository(ConfigStore(storage))
        return ModelsSession(repository, ModelImport(repository, inspector))
    }

    private fun messages(outcome: ModelsSessionOutcome) = outcome.messages

    /** Impor model tujuh kelas; hasilnya profil yang siap diuji lebih lanjut. */
    private fun ModelsSession.importSevenClasses(name: String = "sampah.onnx"): String {
        val outcome = importModel("content://$name", FakeReader(name, onnxBytes))
        assertTrue("impor harus berhasil: ${outcome.messages}", messages(outcome).any {
            it is ModelsSessionMessage.Imported
        })
        return outcome.state.rows.single().profileId
    }

    /** Tetapkan ambang supaya model boleh diaktifkan (MOD-04). */
    private fun ModelsSession.setThreshold(profileId: String) {
        val outcome = commitThreshold(profileId, "0.5")
        assertTrue(
            "ambang harus tersimpan: ${outcome.messages}",
            messages(outcome).any { it is ModelsSessionMessage.ThresholdSaved },
        )
    }

    @Test
    fun importModel_storesProfileWithClassesFromModel() {
        val storage = MemoryConfigStorage()
        val session = session(storage)

        val outcome = session.importModel("content://sampah.onnx", FakeReader("sampah.onnx", onnxBytes))

        assertEquals(
            listOf(ModelsSessionMessage.Imported("sampah")),
            messages(outcome),
        )
        val row = outcome.state.rows.single()
        assertEquals(7, row.classCount)
        assertEquals(sevenClasses, row.labels.map { it.label })
        assertEquals(7, row.labels.mapNotNull { it.wasteClass }.size)
        // Ambang belum ditetapkan pada model yang baru diimpor.
        assertEquals("0", row.thresholdText)
        assertTrue(row.thresholdSet.not())
        assertEquals(1, storage.binaryFiles.size)
    }

    @Test
    fun importModel_jsonFile_isRejectedAndListUnchanged() {
        val session = session(MemoryConfigStorage())
        session.importSevenClasses("lama.onnx")

        val outcome = session.importModel(
            "content://profil.json",
            FakeReader("profil.json", """{"schemaVersion": 1}""".toByteArray()),
        )

        val rejected = messages(outcome).single() as ModelsSessionMessage.ImportRejected
        assertEquals(
            listOf(id.pemilah.model.ModelImportProblemCode.NOT_ONNX_MODEL),
            rejected.problems.map { it.code },
        )
        assertEquals("model lama tetap ada", 1, outcome.state.rows.size)
    }

    @Test
    fun importModel_unreadableModel_isRejected() {
        val session = session(
            MemoryConfigStorage(),
            FakeInspector(
                OnnxInspectionResult.Rejected(
                    OnnxInspectionProblem(OnnxInspectionCode.MISSING_CLASS_NAMES),
                ),
            ),
        )

        val outcome = session.importModel("content://x.onnx", FakeReader("x.onnx", onnxBytes))

        val rejected = messages(outcome).single() as ModelsSessionMessage.ImportRejected
        assertEquals(
            listOf(id.pemilah.model.ModelImportProblemCode.MISSING_CLASS_NAMES),
            rejected.problems.map { it.code },
        )
        assertTrue(outcome.state.isEmpty)
    }

    @Test
    fun activateWithoutThreshold_isRejectedWithThresholdUnset() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val outcome = session.setActiveProfile(profileId, MachineState.Stopped, hasActiveTarget = false)

        val rejected = messages(outcome).single() as ModelsSessionMessage.SwitchRejected
        assertEquals(
            listOf(ProfileSwitchBlockCode.CONFIDENCE_THRESHOLD_UNSET),
            rejected.blocks.map { it.code },
        )
        assertEquals(ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED), session.activeProfile())
    }

    @Test
    fun activateValidProfileWithThreshold_changesActiveState() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        val profileId = session.importSevenClasses()
        session.setThreshold(profileId)

        val outcome = session.setActiveProfile(profileId, MachineState.Stopped, hasActiveTarget = false)

        assertEquals(listOf(ModelsSessionMessage.Activated(profileId)), messages(outcome))
        assertTrue(outcome.state.rows.single().isActive)
        assertTrue(session.activeProfile() is ActiveProfileState.Selected)
    }

    @Test
    fun activateWhileRunning_isRejectedWithMachineRunning() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()
        session.setThreshold(profileId)

        val outcome = session.setActiveProfile(profileId, MachineState.Running, hasActiveTarget = false)

        val rejected = messages(outcome).single() as ModelsSessionMessage.SwitchRejected
        assertEquals(listOf(ProfileSwitchBlockCode.MACHINE_RUNNING), rejected.blocks.map { it.code })
        assertNull(outcome.state.activeProfileId)
    }

    @Test
    fun activateWithActiveTarget_isRejectedWithActiveTargetPresent() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()
        session.setThreshold(profileId)

        val outcome = session.setActiveProfile(profileId, MachineState.Stopped, hasActiveTarget = true)

        val rejected = messages(outcome).single() as ModelsSessionMessage.SwitchRejected
        assertEquals(
            listOf(ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT),
            rejected.blocks.map { it.code },
        )
    }

    @Test
    fun activateUnknownProfile_reportsProfileNotFound() {
        val session = session(MemoryConfigStorage())

        val outcome = session.setActiveProfile("tidak-ada", MachineState.Stopped, false)

        assertEquals(listOf(ModelsSessionMessage.ProfileNotFound("tidak-ada")), messages(outcome))
    }

    @Test
    fun activeProfile_survivesRebuiltSession() {
        val storage = MemoryConfigStorage()
        val first = session(storage)
        val profileId = first.importSevenClasses()
        first.setThreshold(profileId)
        first.setActiveProfile(profileId, MachineState.Stopped, false)

        // Sesi baru dari penyimpanan yang sama, seperti aplikasi dibuka ulang.
        val rebuilt = session(storage)

        val active = rebuilt.activeProfile()
        assertTrue("model aktif harus bertahan: $active", active is ActiveProfileState.Selected)
        assertEquals(profileId, (active as ActiveProfileState.Selected).profile.profileId)
        assertEquals("sampah 8.3.0", rebuilt.operationProfileLabel())
    }

    @Test
    fun commitThreshold_validText_isStored() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val outcome = session.commitThreshold(profileId, "0,62")

        assertEquals(listOf(ModelsSessionMessage.ThresholdSaved(profileId)), messages(outcome))
        assertEquals("0.62", outcome.state.rows.single().thresholdText)
        assertTrue(outcome.state.rows.single().thresholdSet)
    }

    @Test
    fun commitThreshold_nonNumber_isRejectedWithoutChangingProfile() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val outcome = session.commitThreshold(profileId, "abc")

        val message = messages(outcome).single() as ModelsSessionMessage.ThresholdRejected
        assertTrue("alasan field harus dibawa", message.problems.isEmpty())
        assertEquals(
            listOf(ModelsProblemCode.INVALID_VALUE),
            outcome.state.thresholdProblems.getValue(profileId).map { it.code },
        )
        assertEquals("0", outcome.state.rows.single().thresholdText)
    }

    @Test
    fun commitThreshold_aboveOne_isRejectedWithValidatorProblems() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val outcome = session.commitThreshold(profileId, "1.5")

        val message = messages(outcome).single() as ModelsSessionMessage.ThresholdRejected
        assertEquals(
            listOf(id.pemilah.model.ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD),
            message.problems.map { it.reason },
        )
        assertEquals("0", outcome.state.rows.single().thresholdText)
    }

    @Test
    fun draftThreshold_doesNotChangeStoredProfile() {
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val state = session.draftThreshold(profileId, "0.7")

        assertEquals("0.7", state.thresholdTextOf(profileId))
        assertEquals("0", session.load().rows.single().thresholdText)
    }

    @Test
    fun operationProfileLabel_isNullWithoutActiveProfile() {
        val session = session(MemoryConfigStorage())
        session.importSevenClasses()

        assertNull(session.operationProfileLabel())
    }

    @Test
    fun groupingToggle_isStoredButNotExposedOnModelsScreen() {
        // Sakelar kelompok tidak lagi tampil pada layar Models (instruksi pengguna
        // 27 Oktober 2026); logika domainnya tetap ada dan tetap tersimpan.
        val storage = MemoryConfigStorage()
        val session = session(storage)
        val profileId = session.importSevenClasses()
        val repository = ProfileRepository(ConfigStore(storage))

        val outcome = session.toggleGrouping(profileId, enabled = true)

        assertEquals(listOf(ModelsSessionMessage.GroupingSaved(profileId, true)), messages(outcome))
        val stored = repository.find(profileId)
        assertTrue(stored is StoredProfile.Usable)
        assertTrue((stored as StoredProfile.Usable).profile.groupingEnabled)
    }

    @Test
    fun toggleGrouping_unknownProfile_reportsNotUsable() {
        val session = session(MemoryConfigStorage())

        val outcome = session.toggleGrouping("tidak-ada", enabled = true)

        assertEquals(listOf(ModelsSessionMessage.ProfileNotUsable("tidak-ada")), messages(outcome))
    }

    @Test
    fun profileWithModelFileRemoved_isNotUsableAndBlocksActivation() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        val profileId = session.importSevenClasses()
        session.setThreshold(profileId)
        // Berkas model hilang dari penyimpanan aplikasi.
        storage.binaryFiles.clear()

        val outcome = session.setActiveProfile(profileId, MachineState.Stopped, hasActiveTarget = false)

        val rejected = messages(outcome).single() as ModelsSessionMessage.SwitchRejected
        assertEquals(ProfileSwitchBlockCode.PROFILE_INVALID, rejected.blocks.single().code)
        assertEquals(ModelsProfileStatus.INVALID, outcome.state.rows.single().status)
    }

    @Test
    fun storedProfileHelper_usesOfficialClasses() {
        // Penjaga agar fixture test tetap berupa model tujuh kelas resmi.
        val session = session(MemoryConfigStorage())
        val profileId = session.importSevenClasses()

        val row = session.load().rows.single { it.profileId == profileId }

        assertEquals(
            WasteClass.entries.toSet(),
            row.labels.mapNotNull { it.wasteClass }.toSet(),
        )
    }
}
