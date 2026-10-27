package id.pemilah.ui

import id.pemilah.domain.waste.WasteClass
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.InputSpec
import id.pemilah.model.ModelProfile
import id.pemilah.model.Normalization
import id.pemilah.model.OutputFormat
import id.pemilah.model.OutputSpec
import id.pemilah.model.ProfileEditResult
import id.pemilah.model.ProfileJsonProblem
import id.pemilah.model.ProfileJsonProblemReason
import id.pemilah.model.ProfileProblem
import id.pemilah.model.ProfileProblemReason
import id.pemilah.model.ProfileRepository
import id.pemilah.model.ProfileSettings
import id.pemilah.model.ProfileWriteResult
import id.pemilah.model.StoredProfile
import id.pemilah.model.TensorLayout
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proyeksi layar Models (WP-09, D-09): daftar kosong menghasilkan keadaan
 * kosong, model ditolak menampilkan alasannya, model aktif ditandai, label tanpa
 * pemetaan ditandai eksplisit, dan ambang `0.0` terbaca sebagai "belum
 * ditetapkan" (UI-02, MOD-02, MOD-04).
 */
class ModelsUiStateTest {

    private val profile = ModelProfile(
        schemaVersion = 1,
        profileId = "profil-1",
        name = "yolov8-sampah-v2",
        version = "2.0.0",
        modelFile = "model-profil-1.onnx",
        labels = listOf("Organik", "Kertas_lembaran"),
        classMapping = mapOf("Organik" to WasteClass.ORGANIK, "Kertas_lembaran" to WasteClass.KARDUS),
        input = InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = false, hasObjectness = false),
        confidenceThreshold = 0.45,
        groupingEnabled = false,
        binAssignment = mapOf(WasteClass.ORGANIK to "B1", WasteClass.KARDUS to null),
    )

    /** Isi model tiruan; proyeksi hanya peduli keberadaan berkas model. */
    private val modelBytes = byteArrayOf(1, 2, 3)

    private fun usable(profile: ModelProfile = this.profile) = StoredProfile.Usable(
        fileName = "profile-${profile.profileId}.json",
        profile = profile,
    )

    private val noneSelected = ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED)

    @Test
    fun noStoredProfiles_isEmpty() {
        val state = ModelsUiState.from(emptyList(), noneSelected)

        assertTrue(state.isEmpty)
        assertTrue(state.rows.isEmpty())
        assertNull(state.activeProfileId)
    }

    @Test
    fun loading_isNotEmptyAndNotReady() {
        val state = ModelsUiState.from(emptyList(), noneSelected, loading = true)

        assertFalse(state.isEmpty)
        assertTrue(state.loading)
    }

    @Test
    fun usableProfile_rowIsValidWithClassesFromModel() {
        val row = ModelsUiState.from(listOf(usable()), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.VALID, row.status)
        assertEquals("yolov8-sampah-v2", row.name)
        assertEquals("2.0.0", row.version)
        assertTrue(row.problems.isEmpty())
        assertEquals(2, row.classCount)
        assertEquals(
            listOf(
                ModelsLabelUi("Organik", WasteClass.ORGANIK),
                ModelsLabelUi("Kertas_lembaran", WasteClass.KARDUS),
            ),
            row.labels,
        )
    }

    @Test
    fun activeProfile_rowIsMarked() {
        val state = ModelsUiState.from(
            listOf(usable()),
            ActiveProfileState.Selected(profile),
        )

        assertTrue(state.rows.single().isActive)
        assertEquals("profil-1", state.activeProfileId)
    }

    @Test
    fun inactiveProfile_rowIsNotMarked() {
        val other = profile.copy(profileId = "profil-2", name = "lain")

        val state = ModelsUiState.from(
            listOf(usable(), usable(other)),
            ActiveProfileState.Selected(profile),
        )

        assertEquals(listOf(true, false), state.rows.map { it.isActive })
    }

    @Test
    fun profileRejectedByValidator_rowShowsReasonsAndLabels() {
        val broken = StoredProfile.Broken(
            fileName = "profile-profil-1.json",
            profileId = profile.profileId,
            jsonProblems = emptyList(),
            validationProblems = listOf(
                ProfileProblem("classMapping.Kertas_lembaran", ProfileProblemReason.LABEL_NOT_MAPPED),
            ),
            profile = profile.copy(classMapping = mapOf("Organik" to WasteClass.ORGANIK)),
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.INVALID, row.status)
        assertEquals("yolov8-sampah-v2", row.name)
        assertEquals(
            listOf(
                ModelsProblemUi(
                    field = "classMapping.Kertas_lembaran",
                    code = ModelsProblemCode.LABEL_NOT_MAPPED,
                ),
            ),
            row.problems,
        )
        assertEquals(2, row.classCount)
    }

    @Test
    fun unmappedLabel_isMarkedExplicitly() {
        val broken = StoredProfile.Broken(
            fileName = "profile-profil-1.json",
            profileId = profile.profileId,
            jsonProblems = emptyList(),
            validationProblems = listOf(
                ProfileProblem("classMapping.Kertas_lembaran", ProfileProblemReason.LABEL_NOT_MAPPED),
            ),
            profile = profile.copy(classMapping = mapOf("Organik" to WasteClass.ORGANIK)),
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertEquals(
            listOf(
                ModelsLabelUi("Organik", WasteClass.ORGANIK),
                ModelsLabelUi("Kertas_lembaran", null),
            ),
            row.labels,
        )
    }

    @Test
    fun unreadableFile_rowHasNoNameAndJsonReason() {
        val broken = StoredProfile.Broken(
            fileName = "profile-rusak.json",
            profileId = "rusak",
            jsonProblems = listOf(
                ProfileJsonProblem("schemaVersion", ProfileJsonProblemReason.UNKNOWN_SCHEMA_VERSION, "99"),
            ),
            validationProblems = emptyList(),
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.UNREADABLE, row.status)
        assertNull(row.name)
        assertTrue(row.labels.isEmpty())
        assertEquals(0, row.classCount)
        assertEquals(ModelsProblemCode.UNKNOWN_SCHEMA_VERSION, row.problems.single().code)
        assertEquals("99", row.problems.single().detail)
    }

    @Test
    fun modelFileMissing_isReportedAsReason() {
        val broken = StoredProfile.Broken(
            fileName = "profile-profil-1.json",
            profileId = profile.profileId,
            jsonProblems = emptyList(),
            validationProblems = listOf(
                ProfileProblem("modelFile", ProfileProblemReason.MODEL_FILE_MISSING),
            ),
            profile = profile,
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.INVALID, row.status)
        assertEquals(
            ModelsProblemUi("modelFile", ModelsProblemCode.MODEL_FILE_MISSING),
            row.problems.single(),
        )
    }

    @Test
    fun storedThreshold_isShownAsFractionAndPercentWithoutRounding() {
        val row = ModelsUiState.from(listOf(usable()), noneSelected).rows.single()

        assertEquals("0.45", row.thresholdText)
        assertTrue("ambang tersimpan bukan keadaan belum ditetapkan", row.thresholdSet)
        assertEquals("45%", ModelsNumbers.toPercentText(0.45))
        assertEquals("45.5%", ModelsNumbers.toPercentText(0.455))
        assertEquals("0.455", ModelsNumbers.fractionToText(0.455))
    }

    @Test
    fun percentTextFromFloat_roundsToWholePercent() {
        // Nilai keyakinan inferensi bertipe Float; teksnya tidak boleh memamerkan
        // derau pecahan seperti 95.60999870300293%.
        assertEquals("96%", ModelsNumbers.floatToPercentText(0.9560999870300293f))
        assertEquals("3%", ModelsNumbers.floatToPercentText(0.031126385554671288f))
        assertEquals("100%", ModelsNumbers.floatToPercentText(1f))
        assertEquals("0%", ModelsNumbers.floatToPercentText(0f))
    }

    @Test
    fun unsetThreshold_rowReportsThresholdNotSet() {
        // Model hasil impor ONNX menyimpan `0.0` = "belum ditetapkan" (D-09).
        val row = ModelsUiState.from(
            listOf(usable(profile.copy(confidenceThreshold = 0.0))),
            noneSelected,
        ).rows.single()

        assertEquals("0", row.thresholdText)
        assertFalse("ambang 0.0 berarti belum ditetapkan", row.thresholdSet)
    }

    @Test
    fun operatorText_acceptsCommaAndRejectsNonNumbers() {
        assertEquals(0.45, ModelsNumbers.parseFraction("0,45")!!, 0.0)
        assertEquals(0.45, ModelsNumbers.parseFraction(" 0.45 ")!!, 0.0)
        assertNull(ModelsNumbers.parseFraction(""))
        assertNull(ModelsNumbers.parseFraction("abc"))
    }

    @Test
    fun thresholdDraft_overridesStoredTextForThatProfileOnly() {
        val other = profile.copy(profileId = "profil-2", name = "lain", confidenceThreshold = 0.8)
        val state = ModelsUiState.from(
            listOf(usable(), usable(other)),
            ActiveProfileState.Selected(profile),
        )

        val edited = state.withThresholdDraft("profil-2", "0,9")

        assertEquals("0,9", edited.thresholdTextOf("profil-2"))
        assertEquals("0.45", edited.thresholdTextOf("profil-1"))
        assertEquals("0.8", edited.rows.last().thresholdText)
    }

    @Test
    fun thresholdCommit_storesNewValueForOneProfileOnly() {
        val storage = MemoryConfigStorage()
        val repository = ProfileRepository(ConfigStore(storage))
        repository.saveWithModel(profile, modelBytes)
        val other = profile.copy(profileId = "profil-2", name = "lain", modelFile = "model-profil-2.onnx")
        repository.saveWithModel(other, modelBytes)

        val edited = ProfileSettings.withConfidenceThreshold(profile, 0.62)
        assertTrue(edited is ProfileEditResult.Edited)
        val candidate = (edited as ProfileEditResult.Edited).profile
        assertEquals(ProfileWriteResult.Written, repository.save(candidate))

        val state = ModelsUiState.from(repository.list(), noneSelected)

        assertEquals("0.62", state.rows.first { it.profileId == "profil-1" }.thresholdText)
        assertEquals("0.45", state.rows.first { it.profileId == "profil-2" }.thresholdText)
        assertEquals(state.thresholdTextOf("profil-1"), "0.62")
    }

    @Test
    fun invalidThreshold_producesFieldProblemAndLeavesProfileUntouched() {
        val problems = ProfileSettings.withConfidenceThreshold(profile, 1.5)
        assertTrue(problems is ProfileEditResult.Rejected)
        val rejected = (problems as ProfileEditResult.Rejected).problems
            .map { it.toUi() }

        val state = ModelsUiState.from(listOf(usable()), noneSelected)
            .withThresholdProblems("profil-1", rejected)

        assertEquals(
            listOf(
                ModelsProblemUi(
                    field = "confidenceThreshold",
                    code = ModelsProblemCode.INVALID_CONFIDENCE_THRESHOLD,
                ),
            ),
            state.thresholdProblems["profil-1"],
        )
        assertEquals("0.45", state.rows.single().thresholdText)
    }

    @Test
    fun thresholdProblems_clearedWhenEmptyListIsPassed() {
        val state = ModelsUiState()
            .withThresholdProblems("profil-1", listOf(ModelsProblemUi("x", ModelsProblemCode.JSON_INVALID)))
            .withThresholdProblems("profil-1", emptyList())

        assertTrue(state.thresholdProblems.isEmpty())
    }

    @Test
    fun groupingToggle_isStillStoredInDomain() {
        // Sakelar kelompok tidak lagi tampil pada layar Models (instruksi pengguna
        // 27 Oktober 2026), tetapi logika domainnya tetap ada dan tetap tersimpan.
        val storage = MemoryConfigStorage()
        val repository = ProfileRepository(ConfigStore(storage))
        repository.saveWithModel(profile, modelBytes)

        val toggled = ProfileSettings.withGroupingEnabled(profile, true)
        assertTrue(toggled is ProfileEditResult.Edited)
        repository.save((toggled as ProfileEditResult.Edited).profile)

        val stored = repository.find("profil-1")
        assertTrue(stored is StoredProfile.Usable)
        assertTrue((stored as StoredProfile.Usable).profile.groupingEnabled)
    }

    @Test
    fun unreadableProfile_hasNoThresholdValue() {
        val broken = StoredProfile.Broken(
            fileName = "profile-rusak.json",
            profileId = "rusak",
            jsonProblems = listOf(ProfileJsonProblem("", ProfileJsonProblemReason.INVALID_JSON)),
            validationProblems = emptyList(),
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertNull(row.thresholdText)
        assertFalse(row.thresholdSet)
    }

    @Test
    fun activeProfileMissingFile_exposesReason() {
        val state = ModelsUiState.from(
            listOf(usable()),
            ActiveProfileState.None(ActiveProfileAbsentReason.FILE_MISSING, "profil-hantu"),
        )

        assertEquals(ActiveProfileAbsentReason.FILE_MISSING, state.activeAbsentReason)
        assertNull(state.activeProfileId)
        assertFalse(state.rows.single().isActive)
    }
}
