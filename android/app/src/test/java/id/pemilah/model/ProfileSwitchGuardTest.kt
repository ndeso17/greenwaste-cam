package id.pemilah.model

import id.pemilah.domain.operation.MachineState
import id.pemilah.domain.waste.WasteClass
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.MemoryConfigStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Matriks guard pergantian profil (WP-06): berhenti tanpa target diizinkan,
 * berjalan ditolak, berhenti dengan target ditolak, dan profil tujuan tidak sah
 * ditolak sementara profil lama tetap aktif (MOD-02, MOD-03).
 */
class ProfileSwitchGuardTest {

    private fun profile(profileId: String, threshold: Double = 0.45) = ModelProfile(
        schemaVersion = 1,
        profileId = profileId,
        name = "profil-$profileId",
        version = "2.0.0",
        modelFile = "profile-assets/$profileId.onnx",
        labels = listOf("Organik", "Kertas_lembaran"),
        classMapping = mapOf(
            "Organik" to WasteClass.ORGANIK,
            "Kertas_lembaran" to WasteClass.KARDUS,
        ),
        input = InputSpec(640, 640, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.YOLO_V8, boxesNormalized = true, hasObjectness = false),
        confidenceThreshold = threshold,
        groupingEnabled = false,
        binAssignment = mapOf(WasteClass.ORGANIK to "B1", WasteClass.KARDUS to null),
    )

    private val currentProfile = profile("lama")
    private val candidateProfile = profile("baru")

    private fun usable(candidate: ModelProfile) = StoredProfile.Usable(
        fileName = ProfileRepository.fileNameFor(candidate.profileId),
        profile = candidate,
    )

    private fun rejected(decision: ProfileSwitchDecision): List<ProfileSwitchBlockCode> {
        assertTrue("keputusan harus menolak: $decision", decision is ProfileSwitchDecision.Rejected)
        return (decision as ProfileSwitchDecision.Rejected).blocks.map { it.code }
    }

    @Test
    fun stoppedWithoutTarget_isAllowed() {
        assertEquals(
            ProfileSwitchDecision.Allowed,
            ProfileSwitchGuard.evaluate(
                MachineState.Stopped,
                hasActiveTarget = false,
                usable(candidateProfile),
            ),
        )
    }

    @Test
    fun running_isRejectedWithMachineRunningCode() {
        val codes = rejected(
            ProfileSwitchGuard.evaluate(
                MachineState.Running,
                hasActiveTarget = false,
                usable(candidateProfile),
            ),
        )

        assertEquals(listOf(ProfileSwitchBlockCode.MACHINE_RUNNING), codes)
    }

    @Test
    fun stoppedWithActiveTarget_isRejectedWithTargetCode() {
        val codes = rejected(
            ProfileSwitchGuard.evaluate(
                MachineState.Stopped,
                hasActiveTarget = true,
                usable(candidateProfile),
            ),
        )

        assertEquals(listOf(ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT), codes)
    }

    @Test
    fun runningWithActiveTarget_reportsBothReasons() {
        val codes = rejected(
            ProfileSwitchGuard.evaluate(
                MachineState.Running,
                hasActiveTarget = true,
                usable(candidateProfile),
            ),
        )

        assertEquals(
            listOf(
                ProfileSwitchBlockCode.MACHINE_RUNNING,
                ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT,
            ),
            codes,
        )
    }

    @Test
    fun invalidCandidateProfile_isRejectedWithValidatorProblems() {
        val invalid = candidateProfile.copy(
            labels = emptyList(),
            classMapping = emptyMap(),
            binAssignment = emptyMap(),
        )

        val decision = ProfileSwitchGuard.evaluate(
            MachineState.Stopped,
            hasActiveTarget = false,
            invalid,
        )

        assertTrue(decision is ProfileSwitchDecision.Rejected)
        val block = (decision as ProfileSwitchDecision.Rejected).blocks.single()
        assertEquals(ProfileSwitchBlockCode.PROFILE_INVALID, block.code)
        assertTrue(
            "alasan validator harus dibawa: ${block.problems}",
            block.problems.any { it.reason == ProfileProblemReason.NO_LABELS },
        )
    }

    @Test
    fun unsetConfidenceThreshold_isRejectedWithThresholdCode() {
        // Model hasil impor ONNX menyimpan ambang `0.0` = "belum ditetapkan"
        // (D-09). Model itu lolos validator, tetapi tidak boleh diaktifkan
        // sebelum operator menetapkan ambang (MOD-04).
        val unset = candidateProfile.copy(confidenceThreshold = 0.0)

        val codes = rejected(
            ProfileSwitchGuard.evaluate(
                MachineState.Stopped,
                hasActiveTarget = false,
                unset,
            ),
        )

        assertEquals(listOf(ProfileSwitchBlockCode.CONFIDENCE_THRESHOLD_UNSET), codes)
    }

    @Test
    fun setConfidenceThreshold_isAllowed() {
        assertEquals(
            ProfileSwitchDecision.Allowed,
            ProfileSwitchGuard.evaluate(
                MachineState.Stopped,
                hasActiveTarget = false,
                candidateProfile.copy(confidenceThreshold = 0.5),
            ),
        )
    }

    @Test
    fun brokenStoredProfile_isRejectedAsInvalid() {
        val broken = StoredProfile.Broken(
            fileName = "profile-rusak.json",
            profileId = "rusak",
            jsonProblems = listOf(ProfileJsonProblem("", ProfileJsonProblemReason.INVALID_JSON)),
            validationProblems = emptyList(),
        )

        val codes = rejected(
            ProfileSwitchGuard.evaluate(
                MachineState.Stopped,
                hasActiveTarget = false,
                broken,
            ),
        )

        assertEquals(listOf(ProfileSwitchBlockCode.PROFILE_INVALID), codes)
    }

    @Test
    fun rejectedSwitch_leavesActiveProfileUntouched() {
        val repository = ProfileRepository(ConfigStore(MemoryConfigStorage()))
        repository.saveWithModel(currentProfile, MODEL_BYTES)
        repository.saveWithModel(candidateProfile, MODEL_BYTES)
        repository.setActive(currentProfile.profileId)

        val decision = ProfileSwitchGuard.evaluate(
            state = MachineState.Running,
            hasActiveTarget = true,
            candidate = usable(candidateProfile),
        )

        assertTrue(decision is ProfileSwitchDecision.Rejected)
        assertEquals(ActiveProfileState.Selected(currentProfile), repository.active())
    }

    @Test
    fun allowedSwitch_keepsPreviousProfileStoredAndUsable() {
        val repository = ProfileRepository(ConfigStore(MemoryConfigStorage()))
        repository.saveWithModel(currentProfile, MODEL_BYTES)
        repository.saveWithModel(candidateProfile, MODEL_BYTES)
        repository.setActive(currentProfile.profileId)

        val decision = ProfileSwitchGuard.evaluate(
            state = MachineState.Stopped,
            hasActiveTarget = false,
            candidate = usable(candidateProfile),
        )

        assertEquals(ProfileSwitchDecision.Allowed, decision)
        assertEquals(ProfileWriteResult.Written, repository.setActive(candidateProfile.profileId))
        assertEquals(ActiveProfileState.Selected(candidateProfile), repository.active())
        assertEquals(2, repository.list().size)
    }

    private companion object {
        /** Isi model tiruan; yang diuji adalah keberadaan berkas, bukan isinya. */
        val MODEL_BYTES = byteArrayOf(1, 2, 3)
    }
}