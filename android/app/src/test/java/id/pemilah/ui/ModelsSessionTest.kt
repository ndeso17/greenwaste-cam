package id.pemilah.ui

import id.pemilah.domain.operation.MachineState
import id.pemilah.domain.waste.WasteClass
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.InputSpec
import id.pemilah.model.ModelProfile
import id.pemilah.model.Normalization
import id.pemilah.model.OutputFormat
import id.pemilah.model.OutputSpec
import id.pemilah.model.ProfileFileReader
import id.pemilah.model.ProfileImport
import id.pemilah.model.ProfileRepository
import id.pemilah.model.ProfileSwitchBlockCode
import id.pemilah.model.TensorLayout
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sesi layar Models (WP-10): pemilihan profil aktif, penolakan saat berjalan,
 * profil aktif bertahan setelah sesi dibangun ulang, dan label untuk layar
 * Operasi (MOD-03, NFR-03, UI-01).
 */
class ModelsSessionTest {

    private fun profile(profileId: String, name: String = "profil-$profileId") = ModelProfile(
        schemaVersion = 1,
        profileId = profileId,
        name = name,
        version = "2.0.0",
        modelFile = "profile-assets/$profileId.onnx",
        labels = listOf("Organik"),
        classMapping = mapOf("Organik" to WasteClass.ORGANIK),
        input = InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = true, hasObjectness = false),
        confidenceThreshold = 0.45,
        groupingEnabled = false,
        binAssignment = mapOf(WasteClass.ORGANIK to "B1"),
    )

    private fun session(storage: MemoryConfigStorage): ModelsSession {
        val repository = ProfileRepository(ConfigStore(storage))
        return ModelsSession(repository, ProfileImport(repository))
    }

    private fun messages(outcome: ModelsSessionOutcome) = outcome.messages

    @Test
    fun activateValidProfile_changesActiveState() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.setActiveProfile(
            profileId = "profil-1",
            machineState = MachineState.Stopped,
            hasActiveTarget = false,
        )

        assertTrue(messages(outcome).any { it is ModelsSessionMessage.Activated })
        assertEquals("profil-1", outcome.state.activeProfileId)
        assertTrue(outcome.state.rows.single().isActive)
    }

    @Test
    fun activateWhileRunning_isRejectedAndPreviousProfileStaysActive() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))
        session.importProfile("content://b.json", FakeReader(validJson("profil-2")))
        session.setActiveProfile("profil-1", MachineState.Stopped, hasActiveTarget = false)

        val outcome = session.setActiveProfile("profil-2", MachineState.Running, hasActiveTarget = false)

        val blocked = messages(outcome).filterIsInstance<ModelsSessionMessage.SwitchRejected>()
        assertEquals(
            listOf(ProfileSwitchBlockCode.MACHINE_RUNNING),
            blocked.single().blocks.map { it.code },
        )
        assertEquals("profil-1", outcome.state.activeProfileId)
        assertEquals(
            listOf(true, false),
            outcome.state.rows.sortedBy { it.profileId }.map { it.isActive },
        )
    }

    @Test
    fun activateWithActiveTarget_isRejected() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.setActiveProfile("profil-1", MachineState.Stopped, hasActiveTarget = true)

        val blocked = messages(outcome).filterIsInstance<ModelsSessionMessage.SwitchRejected>()
        assertEquals(
            listOf(ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT),
            blocked.single().blocks.map { it.code },
        )
        assertNull(outcome.state.activeProfileId)
    }

    @Test
    fun activateUnknownProfile_reportsNotFound() {
        val session = session(MemoryConfigStorage())

        val outcome = session.setActiveProfile("tidak-ada", MachineState.Stopped, false)

        assertTrue(messages(outcome).single() is ModelsSessionMessage.ProfileNotFound)
    }

    @Test
    fun activeProfile_survivesSessionRebuild() {
        val storage = MemoryConfigStorage()
        session(storage).let { first ->
            first.importProfile("content://a.json", FakeReader(validJson("profil-1")))
            first.setActiveProfile("profil-1", MachineState.Stopped, false)
        }

        val rebuilt = session(storage).load()

        assertEquals("profil-1", rebuilt.activeProfileId)
        assertTrue(rebuilt.rows.single().isActive)
    }

    @Test
    fun noActiveProfile_reportsNotSelected() {
        val state = session(MemoryConfigStorage()).load()

        assertEquals(ActiveProfileAbsentReason.NOT_SELECTED, state.activeAbsentReason)
        assertNull(state.activeProfileId)
    }

    @Test
    fun operationProfileLabel_usesActiveProfileNameAndVersion() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1", "yolov8-v2")))

        assertNull("belum aktif berarti belum ada label", session.operationProfileLabel())

        session.setActiveProfile("profil-1", MachineState.Stopped, false)

        assertEquals("yolov8-v2 2.0.0", session.operationProfileLabel())
    }

    @Test
    fun commitThreshold_storesValueAndClearsProblems() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.commitThreshold("profil-1", "0,62")

        assertTrue(messages(outcome).single() is ModelsSessionMessage.ThresholdSaved)
        assertEquals("0.62", outcome.state.rows.single().thresholdText)
        assertTrue(outcome.state.thresholdProblems.isEmpty())
    }

    @Test
    fun commitThreshold_invalidValue_showsFieldProblemAndKeepsStoredValue() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.commitThreshold("profil-1", "0")

        assertTrue(messages(outcome).single() is ModelsSessionMessage.ThresholdRejected)
        assertEquals(
            ModelsProblemCode.INVALID_CONFIDENCE_THRESHOLD,
            outcome.state.thresholdProblems.getValue("profil-1").single().code,
        )
        assertEquals("0.45", session(storage).load().rows.single().thresholdText)
    }

    @Test
    fun commitThreshold_nonNumber_reportsInvalidValue() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.commitThreshold("profil-1", "abc")

        assertEquals(
            ModelsProblemCode.INVALID_VALUE,
            outcome.state.thresholdProblems.getValue("profil-1").single().code,
        )
        assertEquals("0.45", session(storage).load().rows.single().thresholdText)
    }

    @Test
    fun toggleGrouping_isStoredPerProfile() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))

        val outcome = session.toggleGrouping("profil-1", true)

        assertTrue(messages(outcome).single() is ModelsSessionMessage.GroupingSaved)
        assertEquals(true, outcome.state.rows.single().groupingEnabled)
        assertEquals(true, session(storage).load().rows.single().groupingEnabled)
    }

    @Test
    fun importRejected_keepsExistingProfiles() {
        val storage = MemoryConfigStorage()
        val session = session(storage)
        session.importProfile("content://a.json", FakeReader(validJson("profil-1")))
        val before = storage.files.toMap()

        val outcome = session.importProfile("content://rusak.json", FakeReader("{ bukan json"))

        assertTrue(messages(outcome).single() is ModelsSessionMessage.ImportRejected)
        assertEquals(before, storage.files)
        assertEquals(1, outcome.state.rows.size)
    }

    private class FakeReader(private val content: String?) : ProfileFileReader {
        override fun read(uri: String): String? = content
    }

    private fun validJson(profileId: String, name: String = "profil-$profileId") = """
        {
          "schemaVersion": 1,
          "profileId": "$profileId",
          "name": "$name",
          "version": "2.0.0",
          "modelFile": "profile-assets/$profileId.onnx",
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
}