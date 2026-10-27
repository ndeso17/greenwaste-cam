package id.pemilah.model

import id.pemilah.domain.operation.MachineState

/**
 * Syarat pergantian profil model (MOD-03, MOD-02).
 *
 * Aturan yang dijaga:
 * - Profil hanya boleh diganti saat pemantauan berhenti **dan** tidak ada target
 *   aktif. Keduanya diperiksa terpisah supaya pesan penolakan menyebut sebab
 *   yang sebenarnya, bukan satu kode gabungan.
 * - Profil tujuan wajib lolos [ProfileValidator]; profil rusak atau tidak
 *   kompatibel ditolak dengan alasan per field.
 * - Guard ini murni: ia hanya membaca [MachineState] dan keberadaan target yang
 *   diberikan pemanggil, tidak menyimpan state sendiri dan tidak mengubah profil
 *   aktif (`Architecture.md` §4). Karena itu profil lama tetap aktif bila
 *   keputusannya [ProfileSwitchDecision.Rejected].
 */
object ProfileSwitchGuard {

    /**
     * Periksa apakah [candidate] boleh diaktifkan.
     *
     * @param hasActiveTarget `true` bila siklus pemilahan masih memegang target.
     */
    fun evaluate(
        state: MachineState,
        hasActiveTarget: Boolean,
        candidate: StoredProfile,
    ): ProfileSwitchDecision {
        val stateBlocks = buildList {
            if (state != MachineState.Stopped) {
                add(ProfileSwitchBlock(ProfileSwitchBlockCode.MACHINE_RUNNING))
            }
            if (hasActiveTarget) {
                add(ProfileSwitchBlock(ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT))
            }
        }
        val profileBlocks = when (candidate) {
            is StoredProfile.Usable -> profileBlocks(candidate.profile)
            // Profil yang gagal decode tidak punya keluhan validator; rinciannya
            // tetap tersedia pada `candidate.jsonProblems` bagi pemanggil.
            is StoredProfile.Broken -> listOf(
                ProfileSwitchBlock(
                    code = ProfileSwitchBlockCode.PROFILE_INVALID,
                    problems = candidate.validationProblems,
                ),
            )
        }
        val blocks = stateBlocks + profileBlocks
        return if (blocks.isEmpty()) {
            ProfileSwitchDecision.Allowed
        } else {
            ProfileSwitchDecision.Rejected(blocks)
        }
    }

    /** Varian untuk profil yang belum tersimpan. */
    fun evaluate(
        state: MachineState,
        hasActiveTarget: Boolean,
        candidate: ModelProfile,
    ): ProfileSwitchDecision = evaluate(
        state = state,
        hasActiveTarget = hasActiveTarget,
        candidate = StoredProfile.Usable(
            fileName = ProfileRepository.fileNameFor(candidate.profileId),
            profile = candidate,
        ),
    )

    private fun profileBlocks(candidate: ModelProfile): List<ProfileSwitchBlock> =
        when (val validation = ProfileValidator.validate(candidate)) {
            is ProfileValidation.Valid -> emptyList()
            is ProfileValidation.Rejected -> listOf(
                ProfileSwitchBlock(
                    code = ProfileSwitchBlockCode.PROFILE_INVALID,
                    problems = validation.problems,
                ),
            )
        }
}

/** Keputusan pergantian profil; penolakan tidak pernah mengubah profil aktif. */
sealed interface ProfileSwitchDecision {
    data object Allowed : ProfileSwitchDecision
    data class Rejected(val blocks: List<ProfileSwitchBlock>) : ProfileSwitchDecision
}

/** Satu sebab pergantian profil ditolak, dengan kode stabil untuk UI. */
data class ProfileSwitchBlock(
    val code: ProfileSwitchBlockCode,
    val problems: List<ProfileProblem> = emptyList(),
)

enum class ProfileSwitchBlockCode {
    /** Pemantauan masih berjalan (MOD-03). */
    MACHINE_RUNNING,

    /** Robot masih memegang target pada siklus berjalan (MOD-03). */
    ACTIVE_TARGET_PRESENT,

    /** Profil tujuan tidak lolos decode atau validasi (MOD-02). */
    PROFILE_INVALID,
}
