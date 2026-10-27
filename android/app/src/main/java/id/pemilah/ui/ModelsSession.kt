package id.pemilah.ui

import id.pemilah.domain.operation.MachineState
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.ProfileEditResult
import id.pemilah.model.ProfileFileReader
import id.pemilah.model.ProfileImport
import id.pemilah.model.ProfileImportProblem
import id.pemilah.model.ProfileImportResult
import id.pemilah.model.ProfileProblem
import id.pemilah.model.ProfileRepository
import id.pemilah.model.ProfileSettings
import id.pemilah.model.ProfileSwitchBlock
import id.pemilah.model.ProfileSwitchDecision
import id.pemilah.model.ProfileSwitchGuard
import id.pemilah.model.ProfileWriteResult
import id.pemilah.model.StoredProfile

/**
 * Pemilik tunggal state layar Models sekaligus penyambung ke layar Operasi
 * (`Architecture.md` §4, MOD-03, NFR-03).
 *
 * Aturan yang dijaga:
 * - Satu sumber kebenaran: seluruh state dibaca dari [ProfileRepository]; kelas
 *   ini tidak menyimpan salinan profil di memori, sehingga layar Models dan
 *   layar Operasi tidak dapat menampilkan nilai berbeda.
 * - Setiap aksi mengembalikan [ModelsSessionOutcome] berisi state terbaru dan
 *   pesan bertipe. Tidak ada pengecualian yang lolos ke Composable.
 * - Aktivasi profil selalu melewati [ProfileSwitchGuard]; saat ditolak, penanda
 *   profil aktif tidak diubah dan alasannya dikembalikan.
 * - Repositori disimpan sehingga profil aktif bertahan setelah aplikasi ditutup.
 */
class ModelsSession(
    private val repository: ProfileRepository,
    private val importer: ProfileImport,
) {

    /** Muat state dari penyimpanan; `loading` dipakai saat pemuatan awal. */
    fun load(loading: Boolean = false): ModelsUiState =
        ModelsUiState.from(repository.list(), repository.active(), loading)

    /** Impor berkas profil terpilih; kegagalan tidak mengubah daftar profil. */
    fun importProfile(uri: String, reader: ProfileFileReader): ModelsSessionOutcome =
        when (val result = importer.import(uri, reader)) {
            is ProfileImportResult.Imported -> ModelsSessionOutcome(
                state = load(),
                messages = listOf(ModelsSessionMessage.Imported(result.profile.name)),
            )

            is ProfileImportResult.Rejected -> ModelsSessionOutcome(
                state = load(),
                messages = listOf(ModelsSessionMessage.ImportRejected(result.problems)),
            )
        }

    /**
     * Jadikan profil aktif.
     *
     * [machineState] dan [hasActiveTarget] diberikan pemanggil karena pemilik
     * state operasi bukan kelas ini; guard tetap satu-satunya yang memutuskan.
     */
    fun setActiveProfile(
        profileId: String,
        machineState: MachineState,
        hasActiveTarget: Boolean,
    ): ModelsSessionOutcome {
        val candidate = repository.find(profileId)
            ?: return ModelsSessionOutcome(
                state = load(),
                messages = listOf(ModelsSessionMessage.ProfileNotFound(profileId)),
            )
        return when (
            val decision = ProfileSwitchGuard.evaluate(machineState, hasActiveTarget, candidate)
        ) {
            is ProfileSwitchDecision.Allowed -> when (val saved = repository.setActive(profileId)) {
                is ProfileWriteResult.Written -> ModelsSessionOutcome(
                    state = load(),
                    messages = listOf(ModelsSessionMessage.Activated(profileId)),
                )

                else -> ModelsSessionOutcome(
                    state = load(),
                    messages = listOf(ModelsSessionMessage.StorageFailed(saved.detail())),
                )
            }

            is ProfileSwitchDecision.Rejected -> ModelsSessionOutcome(
                state = load(),
                messages = listOf(ModelsSessionMessage.SwitchRejected(decision.blocks)),
            )
        }
    }

    /** Draf teks ambang operator; belum mengubah profil tersimpan (MOD-04). */
    fun draftThreshold(profileId: String, text: String): ModelsUiState =
        load().withThresholdDraft(profileId, text)

    /**
     * Terapkan draf ambang. Nilai tidak sah ditolak dengan alasan pada field dan
     * profil tersimpan tidak berubah.
     */
    fun commitThreshold(profileId: String, text: String): ModelsSessionOutcome {
        val state = load().withThresholdDraft(profileId, text)
        val value = ModelsNumbers.parseFraction(text)
            ?: return ModelsSessionOutcome(
                state = state.withThresholdProblems(
                    profileId,
                    listOf(
                        ModelsProblemUi(
                            field = "confidenceThreshold",
                            code = ModelsProblemCode.INVALID_VALUE,
                            detail = text,
                        ),
                    ),
                ),
                messages = listOf(ModelsSessionMessage.ThresholdRejected(profileId, emptyList())),
            )
        val profile = (repository.find(profileId) as? StoredProfile.Usable)?.profile
            ?: return ModelsSessionOutcome(
                state = state,
                messages = listOf(ModelsSessionMessage.ProfileNotUsable(profileId)),
            )
        return when (val edited = ProfileSettings.withConfidenceThreshold(profile, value)) {
            is ProfileEditResult.Edited -> when (val saved = repository.save(edited.profile)) {
                is ProfileWriteResult.Written -> ModelsSessionOutcome(
                    state = load(),
                    messages = listOf(ModelsSessionMessage.ThresholdSaved(profileId)),
                )

                else -> ModelsSessionOutcome(
                    state = state,
                    messages = listOf(ModelsSessionMessage.StorageFailed(saved.detail())),
                )
            }

            is ProfileEditResult.Rejected -> ModelsSessionOutcome(
                state = state.withThresholdProblems(profileId, edited.problems.map { it.toUi() }),
                messages = listOf(ModelsSessionMessage.ThresholdRejected(profileId, edited.problems)),
            )
        }
    }

    /** Simpan sakelar kelompok per profil (MOD-06). Perilaku kelompok menunggu B-07. */
    fun toggleGrouping(profileId: String, enabled: Boolean): ModelsSessionOutcome {
        val profile = (repository.find(profileId) as? StoredProfile.Usable)?.profile
            ?: return ModelsSessionOutcome(
                state = load(),
                messages = listOf(ModelsSessionMessage.ProfileNotUsable(profileId)),
            )
        return when (val edited = ProfileSettings.withGroupingEnabled(profile, enabled)) {
            is ProfileEditResult.Edited -> when (val saved = repository.save(edited.profile)) {
                is ProfileWriteResult.Written -> ModelsSessionOutcome(
                    state = load(),
                    messages = listOf(ModelsSessionMessage.GroupingSaved(profileId, enabled)),
                )

                else -> ModelsSessionOutcome(
                    state = load(),
                    messages = listOf(ModelsSessionMessage.StorageFailed(saved.detail())),
                )
            }

            is ProfileEditResult.Rejected -> ModelsSessionOutcome(
                state = load(),
                messages = listOf(
                    ModelsSessionMessage.StorageFailed(
                        edited.problems.joinToString(",") { it.field },
                    ),
                ),
            )
        }
    }

    /** Nama dan versi profil aktif untuk layar Operasi; `null` bila belum aktif. */
    fun operationProfileLabel(state: ModelsUiState = load()): String? =
        state.rows.firstOrNull { it.isActive }?.let { row ->
            val name = row.name ?: return@let null
            row.version?.let { version -> "$name $version" } ?: name
        }

    /** Profil aktif yang sah, atau `null`; dipakai layar lain tanpa menyimpan salinan. */
    fun activeProfile(): ActiveProfileState = repository.active()
}

/** State terbaru beserta pesan hasil aksi; pesan bertipe agar teks tetap di UI. */
data class ModelsSessionOutcome(
    val state: ModelsUiState,
    val messages: List<ModelsSessionMessage> = emptyList(),
)

/** Hasil satu aksi pada layar Models. */
sealed interface ModelsSessionMessage {
    data class Imported(val profileName: String) : ModelsSessionMessage
    data class ImportRejected(val problems: List<ProfileImportProblem>) :
        ModelsSessionMessage

    data class Activated(val profileId: String) : ModelsSessionMessage
    data class SwitchRejected(val blocks: List<ProfileSwitchBlock>) : ModelsSessionMessage
    data class ProfileNotFound(val profileId: String) : ModelsSessionMessage
    data class ProfileNotUsable(val profileId: String) : ModelsSessionMessage
    data class ThresholdRejected(val profileId: String, val problems: List<ProfileProblem>) :
        ModelsSessionMessage

    data class ThresholdSaved(val profileId: String) : ModelsSessionMessage
    data class GroupingSaved(val profileId: String, val enabled: Boolean) : ModelsSessionMessage
    data class StorageFailed(val detail: String?) : ModelsSessionMessage
}

private fun ProfileWriteResult.detail(): String? = when (this) {
    is ProfileWriteResult.StorageFailed -> detail
    is ProfileWriteResult.NotFound -> profileId
    is ProfileWriteResult.Unusable -> stored.fileName
    is ProfileWriteResult.Rejected -> problems.joinToString(",") { it.field }
    is ProfileWriteResult.Written -> null
}
