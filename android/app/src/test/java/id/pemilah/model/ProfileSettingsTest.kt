package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti penyuntingan profil (WP-05): ambang tidak sah ditolak tanpa mutasi,
 * sakelar kelompok tersimpan, pemetaan bak menolak kelas di luar profil dan nama
 * kosong, serta semua suntingan lolos [ProfileValidator] (MOD-04, MOD-05, MOD-06).
 */
class ProfileSettingsTest {

    private val base = ModelProfile(
        schemaVersion = 1,
        profileId = "6f1a1d2c-0000-4000-8000-000000000001",
        name = "yolov8-sampah-v2",
        version = "2.0.0",
        modelFile = "profile-assets/yolov8-sampah-v2.onnx",
        labels = listOf("Organik", "Kertas_lembaran"),
        classMapping = mapOf(
            "Organik" to WasteClass.ORGANIK,
            "Kertas_lembaran" to WasteClass.KARDUS,
        ),
        input = InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = true, hasObjectness = false),
        confidenceThreshold = 0.45,
        groupingEnabled = false,
        binAssignment = mapOf(WasteClass.ORGANIK to "B1", WasteClass.KARDUS to null),
    )

    private fun edited(result: ProfileEditResult): ModelProfile {
        assertTrue("suntingan harus diterima: $result", result is ProfileEditResult.Edited)
        return (result as ProfileEditResult.Edited).profile
    }

    private fun rejected(result: ProfileEditResult): List<ProfileProblem> {
        assertTrue("suntingan harus ditolak: $result", result is ProfileEditResult.Rejected)
        return (result as ProfileEditResult.Rejected).problems
    }

    @Test
    fun confidenceThreshold_withinOpenClosedRange_isAccepted() {
        val one = edited(ProfileSettings.withConfidenceThreshold(base, 1.0))
        val small = edited(ProfileSettings.withConfidenceThreshold(base, 0.01))

        assertEquals(1.0, one.confidenceThreshold, 0.0)
        assertEquals(0.01, small.confidenceThreshold, 0.0)
    }

    @Test
    fun confidenceThreshold_zero_isRejectedAndLeavesProfileUnchanged() {
        val problems = rejected(ProfileSettings.withConfidenceThreshold(base, 0.0))

        assertTrue(
            problems.any { it.reason == ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD },
        )
        assertEquals(0.45, base.confidenceThreshold, 0.0)
    }

    @Test
    fun confidenceThreshold_aboveOneAndNonFinite_areRejected() {
        listOf(1.0001, Double.POSITIVE_INFINITY, Double.NaN).forEach { value ->
            val problems = rejected(ProfileSettings.withConfidenceThreshold(base, value))
            assertTrue(
                "nilai $value harus ditolak dengan alasan bertipe: $problems",
                problems.any { it.reason == ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD },
            )
        }
        assertEquals(0.45, base.confidenceThreshold, 0.0)
    }

    @Test
    fun groupingEnabled_isStoredPerProfile() {
        val enabled = edited(ProfileSettings.withGroupingEnabled(base, true))

        assertTrue(enabled.groupingEnabled)
        assertTrue("sakelar tidak mengubah field lain", base.groupingEnabled.not())
        assertEquals(base.labels, enabled.labels)
    }

    @Test
    fun binAssignment_nullMeansNotAssignedAndIsKeptAsNull() {
        val cleared = edited(
            ProfileSettings.withBinAssignment(base, WasteClass.ORGANIK, null),
        )

        assertTrue(cleared.binAssignment.containsKey(WasteClass.ORGANIK))
        assertEquals(null, cleared.binAssignment[WasteClass.ORGANIK])
    }

    @Test
    fun binAssignment_assignsByName() {
        val assigned = edited(
            ProfileSettings.withBinAssignment(base, WasteClass.KARDUS, "B4"),
        )

        assertEquals("B4", assigned.binAssignment[WasteClass.KARDUS])
    }

    @Test
    fun binAssignment_classNotMappedByProfile_isRejected() {
        val problems = rejected(
            ProfileSettings.withBinAssignment(base, WasteClass.KACA, "B3"),
        )

        assertTrue(
            "kelas di luar profil harus ditolak: $problems",
            problems.any { it.reason == ProfileProblemReason.CLASS_NOT_IN_PROFILE },
        )
        assertEquals(setOf(WasteClass.ORGANIK, WasteClass.KARDUS), base.binAssignment.keys)
    }

    @Test
    fun binAssignment_blankName_isRejectedButNullIsNot() {
        val problems = rejected(ProfileSettings.withBinAssignment(base, WasteClass.KARDUS, "   "))

        assertTrue(problems.any { it.reason == ProfileProblemReason.INVALID_BIN_NAME })
        assertTrue(ProfileSettings.withBinAssignment(base, WasteClass.KARDUS, null)
            is ProfileEditResult.Edited)
    }

    @Test
    fun everyRejectedEdit_leavesInputProfileByteIdentical() {
        val snapshot = ProfileJson.encode(base)

        ProfileSettings.withConfidenceThreshold(base, 0.0)
        ProfileSettings.withBinAssignment(base, WasteClass.KACA, "B3")
        ProfileSettings.withBinAssignment(base, WasteClass.KARDUS, "")

        assertEquals(snapshot, ProfileJson.encode(base))
    }

    @Test
    fun assignableClasses_listsOnlyOfficialClassesMappedByProfile() {
        val classes = ProfileSettings.assignableClasses(base)

        assertEquals(listOf(WasteClass.ORGANIK, WasteClass.KARDUS), classes)
        // Tujuh kelas resmi tetap terikat pada enum; tidak ada kelas tambahan.
        assertEquals(7, WasteClass.entries.size)
    }

    @Test
    fun editedProfile_alwaysPassesValidator() {
        val editedProfile = edited(ProfileSettings.withGroupingEnabled(base, true))

        assertEquals(ProfileValidation.Valid, ProfileValidator.validate(editedProfile))
    }
}