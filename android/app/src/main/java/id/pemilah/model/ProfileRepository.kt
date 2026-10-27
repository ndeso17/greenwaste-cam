package id.pemilah.model

import id.pemilah.storage.ConfigStore
import id.pemilah.storage.SaveResult
import id.pemilah.storage.StoreProblemReason

/**
 * Pemilik tunggal profil model tersimpan dan profil aktif
 * (`Architecture.md` §4, MOD-01, MOD-03, NFR-03).
 *
 * Aturan yang dijaga:
 * - Profil disimpan sebagai `profile-<profileId>.json` lewat
 *   [ConfigStore.writeProfileFile] sehingga memakai jalur tulis atomik yang
 *   sudah ada; tulisan gagal tidak merusak profil lama.
 * - Profil yang gagal dibaca atau gagal divalidasi **tetap terdaftar** dengan
 *   alasannya. Tidak ada penghapusan otomatis dan tidak ada nilai bawaan yang
 *   menutupi kerusakan (`Rules.md` §2.2).
 * - `profileId` aktif disimpan terpisah pada `active-profile.txt`. Profil aktif
 *   yang berkasnya hilang atau tidak lagi valid dilaporkan sebagai
 *   [ActiveProfileState.None] beserta alasannya, bukan sebagai galat.
 * - Repositori tidak menyimpan salinan state di memori; setiap pembacaan
 *   mengambil keadaan terbaru dari penyimpanan sehingga layar Operasi dan Models
 *   tidak dapat menampilkan nilai berbeda.
 */
class ProfileRepository(private val store: ConfigStore) {

    /** Seluruh profil tersimpan beserta hasil validasinya, urut nama berkas. */
    fun list(): List<StoredProfile> = store.listProfileFiles().map { name -> readStored(name) }

    /** Profil tersimpan tertentu; `null` bila berkasnya tidak ada. */
    fun find(profileId: String): StoredProfile? {
        val name = fileNameFor(profileId)
        if (!store.profileFileExists(name)) return null
        return readStored(name)
    }

    /**
     * Simpan profil.
     *
     * Profil yang tidak lolos [ProfileValidator] **tidak ditulis**; penolakan
     * membawa alasan per field sehingga layar Models dapat menunjukkannya
     * (MOD-02).
     */
    fun save(profile: ModelProfile): ProfileWriteResult {
        val validation = ProfileValidator.validate(profile)
        if (validation is ProfileValidation.Rejected) {
            return ProfileWriteResult.Rejected(validation.problems)
        }
        return store.writeProfileFile(fileNameFor(profile.profileId), ProfileJson.encode(profile))
            .toWriteResult()
    }

    /** Profil aktif yang sah, atau [ActiveProfileState.None] beserta alasannya. */
    fun active(): ActiveProfileState {
        val profileId = store.readActiveProfileId()
            ?: return ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED)
        val stored = find(profileId)
            ?: return ActiveProfileState.None(ActiveProfileAbsentReason.FILE_MISSING, profileId)
        return when (stored) {
            is StoredProfile.Usable -> ActiveProfileState.Selected(stored.profile)
            is StoredProfile.Broken -> ActiveProfileState.None(
                ActiveProfileAbsentReason.NOT_VALID,
                profileId,
            )
        }
    }

    /**
     * Tetapkan profil aktif. Hanya profil yang lolos validasi boleh dipilih;
     * profil rusak ditolak dengan alasannya supaya penanda aktif tidak pernah
     * menunjuk profil yang tidak dapat dipakai (MOD-03).
     */
    fun setActive(profileId: String): ProfileWriteResult =
        when (val stored = find(profileId)) {
            null -> ProfileWriteResult.NotFound(profileId)
            is StoredProfile.Broken -> ProfileWriteResult.Unusable(stored)
            is StoredProfile.Usable -> store.writeActiveProfileId(profileId).toWriteResult()
        }

    private fun readStored(fileName: String): StoredProfile {
        val text = store.readProfileFile(fileName)
            ?: return StoredProfile.Broken(
                fileName = fileName,
                profileId = profileIdFrom(fileName),
                jsonProblems = listOf(
                    ProfileJsonProblem(
                        field = "",
                        reason = ProfileJsonProblemReason.INVALID_JSON,
                        detail = "berkas tidak dapat dibaca",
                    ),
                ),
                validationProblems = emptyList(),
            )
        return when (val decoded = ProfileJson.decode(text)) {
            is ProfileDecodeResult.Malformed -> StoredProfile.Broken(
                fileName = fileName,
                profileId = profileIdFrom(fileName),
                jsonProblems = decoded.problems,
                validationProblems = emptyList(),
            )

            is ProfileDecodeResult.Decoded -> when (
                val validation = ProfileValidator.validate(decoded.profile)
            ) {
                is ProfileValidation.Valid -> StoredProfile.Usable(fileName, decoded.profile)
                is ProfileValidation.Rejected -> StoredProfile.Broken(
                    fileName = fileName,
                    profileId = decoded.profile.profileId,
                    jsonProblems = emptyList(),
                    validationProblems = validation.problems,
                )
            }
        }
    }

    private fun SaveResult.toWriteResult(): ProfileWriteResult = when (this) {
        is SaveResult.Saved -> ProfileWriteResult.Written
        is SaveResult.Failed -> ProfileWriteResult.StorageFailed(reason, detail)
    }

    companion object {
        fun fileNameFor(profileId: String): String =
            "${ConfigStore.PROFILE_PREFIX}$profileId${ConfigStore.PROFILE_EXTENSION}"

        fun profileIdFrom(fileName: String): String = fileName
            .removePrefix(ConfigStore.PROFILE_PREFIX)
            .removeSuffix(ConfigStore.PROFILE_EXTENSION)
    }
}

/** Satu profil tersimpan; rusak bukan berarti hilang (MOD-01, MOD-02). */
sealed interface StoredProfile {
    val fileName: String

    data class Usable(override val fileName: String, val profile: ModelProfile) : StoredProfile

    data class Broken(
        override val fileName: String,
        val profileId: String,
        val jsonProblems: List<ProfileJsonProblem>,
        val validationProblems: List<ProfileProblem>,
    ) : StoredProfile
}

/** Hasil penulisan profil atau profil aktif. */
sealed interface ProfileWriteResult {
    data object Written : ProfileWriteResult

    /** Profil tidak lolos validasi; tidak ada berkas yang diubah. */
    data class Rejected(val problems: List<ProfileProblem>) : ProfileWriteResult

    /** `profileId` tidak memiliki berkas profil. */
    data class NotFound(val profileId: String) : ProfileWriteResult

    /** Profil ada tetapi rusak; penanda aktif tidak diubah. */
    data class Unusable(val stored: StoredProfile.Broken) : ProfileWriteResult

    /** Penyimpanan menolak tulisan; profil lama tetap utuh. */
    data class StorageFailed(
        val reason: StoreProblemReason,
        val detail: String? = null,
    ) : ProfileWriteResult
}

/** Keadaan profil aktif; tidak pernah berupa galat bagi pemanggil. */
sealed interface ActiveProfileState {
    data class Selected(val profile: ModelProfile) : ActiveProfileState

    data class None(
        val reason: ActiveProfileAbsentReason,
        val profileId: String? = null,
    ) : ActiveProfileState
}

enum class ActiveProfileAbsentReason {
    /** Belum ada profil yang pernah dipilih. */
    NOT_SELECTED,

    /** `profileId` tercatat tetapi berkasnya tidak ada lagi. */
    FILE_MISSING,

    /** Berkas ada tetapi tidak lolos decode atau validasi. */
    NOT_VALID,
}
