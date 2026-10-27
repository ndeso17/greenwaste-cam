package id.pemilah.ui

import id.pemilah.domain.waste.WasteClass
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.InputSpec
import id.pemilah.model.ModelProfile
import id.pemilah.model.Normalization
import id.pemilah.model.OutputFormat
import id.pemilah.model.OutputSpec
import id.pemilah.model.ProfileJsonProblem
import id.pemilah.model.ProfileJsonProblemReason
import id.pemilah.model.ProfileProblem
import id.pemilah.model.ProfileProblemReason
import id.pemilah.model.StoredProfile
import id.pemilah.model.TensorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proyeksi layar Models (WP-07): daftar kosong menghasilkan keadaan kosong,
 * profil ditolak menampilkan alasannya, profil aktif ditandai, dan label tanpa
 * pemetaan ditandai eksplisit (UI-02, MOD-02).
 */
class ModelsUiStateTest {

    private val profile = ModelProfile(
        schemaVersion = 1,
        profileId = "profil-1",
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
    fun usableProfile_rowIsValidWithLabelsAndBins() {
        val row = ModelsUiState.from(listOf(usable()), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.VALID, row.status)
        assertEquals("yolov8-sampah-v2", row.name)
        assertEquals("2.0.0", row.version)
        assertTrue(row.problems.isEmpty())
        assertEquals(
            listOf(
                ModelsLabelUi("Organik", WasteClass.ORGANIK, "B1"),
                ModelsLabelUi("Kertas_lembaran", WasteClass.KARDUS, null),
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
                ProfileProblem("confidenceThreshold", ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD),
            ),
            profile = profile.copy(confidenceThreshold = 0.0),
        )

        val row = ModelsUiState.from(listOf(broken), noneSelected).rows.single()

        assertEquals(ModelsProfileStatus.INVALID, row.status)
        assertEquals("yolov8-sampah-v2", row.name)
        assertEquals(
            listOf(
                ModelsProblemUi(
                    field = "confidenceThreshold",
                    code = ModelsProblemCode.INVALID_CONFIDENCE_THRESHOLD,
                ),
            ),
            row.problems,
        )
        assertEquals(2, row.labels.size)
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
                ModelsLabelUi("Organik", WasteClass.ORGANIK, "B1"),
                ModelsLabelUi("Kertas_lembaran", null, null),
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
        assertEquals(ModelsProblemCode.UNKNOWN_SCHEMA_VERSION, row.problems.single().code)
        assertEquals("99", row.problems.single().detail)
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