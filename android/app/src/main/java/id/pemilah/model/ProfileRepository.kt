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

    /**
     * Simpan profil beserta salinan berkas modelnya (D-09, MOD-01, NFR-03).
     *
     * Urutan tulisan: berkas model lebih dulu, profil sesudahnya. Profil adalah
     * penanda bahwa model boleh dipakai; menulis profil lebih dulu akan
     * meninggalkan profil yang menunjuk model yang belum ada. Bila tulisan
     * profil gagal, berkas model yatim yang tertinggal tidak pernah dipakai
     * karena tidak ada profil yang menunjuknya.
     */
    fun saveWithModel(profile: ModelProfile, modelBytes: ByteArray): ProfileWriteResult {
        val validation = ProfileValidator.validate(profile)
        if (validation is ProfileValidation.Rejected) {
            return ProfileWriteResult.Rejected(validation.problems)
        }
        val modelWrite = store.writeModelFile(profile.modelFile, modelBytes)
        if (modelWrite is SaveResult.Failed) return modelWrite.toWriteResult()
        return save(profile)
    }

    /** `true` bila `profileId` sudah dipakai profil atau berkas modelnya (MOD-01). */
    fun exists(profileId: String): Boolean =
        store.profileFileExists(fileNameFor(profileId)) ||
            store.modelFileExists(modelFileNameFor(profileId))

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

    /**
     * Hapus profil beserta salinan berkas modelnya (instruksi pengguna
     * 27 Oktober 2026).
     *
     * Aturan yang dijaga:
     * - Penghapusan hanya berjalan atas permintaan operator; aplikasi tidak
     *   pernah menghapus profil sendiri, termasuk profil rusak (`Rules.md` §2.2).
     * - Bila profil yang dihapus sedang aktif, penanda profil aktif dikosongkan
     *   sehingga `active()` menjadi [ActiveProfileAbsentReason.NOT_SELECTED],
     *   bukan menunjuk profil yang sudah tidak ada.
     * - Profil lain tidak tersentuh: daftar dibaca ulang dari penyimpanan
     *   sesudah penghapusan.
     */
    fun delete(profileId: String): ProfileDeleteResult {
        val fileName = fileNameFor(profileId)
        val modelFile = modelFileNameFor(profileId)
        val profileExisted = store.profileFileExists(fileName)
        val modelExisted = store.modelFileExists(modelFile)
        if (!profileExisted && !modelExisted) return ProfileDeleteResult.NotFound(profileId)

        // Profil dihapus lebih dulu: selama berkas profil ada, berkas model
        // dianggap masih dipakai. Urutan ini tidak meninggalkan profil yang
        // menunjuk model yang sudah dihapus.
        if (profileExisted && !store.deleteProfileFile(fileName)) {
            return ProfileDeleteResult.StorageFailed(StoreProblemReason.WRITE_FAILED, fileName)
        }
        if (modelExisted && !store.deleteModelFile(modelFile)) {
            // Profil sudah tidak ada; berkas model yatim tidak dirujuk siapa pun.
            return ProfileDeleteResult.StorageFailed(StoreProblemReason.WRITE_FAILED, modelFile)
        }
        if (store.readActiveProfileId() == profileId) {
            store.clearActiveProfileId()
        }
        return ProfileDeleteResult.Deleted(
            profileId = profileId,
            modelFileDeleted = modelExisted,
        )
    }

    private fun readStored(fileName: String): StoredProfile {        val text = store.readProfileFile(fileName)
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
                is ProfileValidation.Valid -> modelFileProblems(decoded.profile).let { problems ->
                    if (problems.isEmpty()) {
                        StoredProfile.Usable(fileName, decoded.profile)
                    } else {
                        StoredProfile.Broken(
                            fileName = fileName,
                            profileId = decoded.profile.profileId,
                            jsonProblems = emptyList(),
                            validationProblems = problems,
                            profile = decoded.profile,
                        )
                    }
                }

                is ProfileValidation.Rejected -> StoredProfile.Broken(
                    fileName = fileName,
                    profileId = decoded.profile.profileId,
                    jsonProblems = emptyList(),
                    validationProblems = validation.problems,
                    profile = decoded.profile,
                )
            }
        }
    }

    /**
     * Keluhan tentang berkas model yang dirujuk profil (D-09, MOD-01).
     *
     * Pemeriksaan ini ada di repositori, bukan di [ProfileValidator], karena
     * validator bersifat murni dan tidak membaca penyimpanan. Profil yang
     * modelnya hilang tetap terdaftar dengan alasannya, bukan dihapus diam-diam.
     */
    private fun modelFileProblems(profile: ModelProfile): List<ProfileProblem> =
        if (profile.modelFile.isBlank() || !store.modelFileExists(profile.modelFile)) {
            listOf(ProfileProblem("modelFile", ProfileProblemReason.MODEL_FILE_MISSING))
        } else {
            emptyList()
        }

    private fun SaveResult.toWriteResult(): ProfileWriteResult = when (this) {
        is SaveResult.Saved -> ProfileWriteResult.Written
        is SaveResult.Failed -> ProfileWriteResult.StorageFailed(reason, detail)
    }

    companion object {
        fun fileNameFor(profileId: String): String =
            "${ConfigStore.PROFILE_PREFIX}$profileId${ConfigStore.PROFILE_EXTENSION}"

        /** Nama berkas salinan model ONNX milik satu profil (D-09). */
        fun modelFileNameFor(profileId: String): String =
            "${ConfigStore.MODEL_PREFIX}$profileId${ConfigStore.MODEL_EXTENSION}"

        fun profileIdFrom(fileName: String): String = fileName
            .removePrefix(ConfigStore.PROFILE_PREFIX)
            .removeSuffix(ConfigStore.PROFILE_EXTENSION)
    }
}

/** Satu profil tersimpan; rusak bukan berarti hilang (MOD-01, MOD-02). */
sealed interface StoredProfile {
    val fileName: String

    data class Usable(override val fileName: String, val profile: ModelProfile) : StoredProfile

    /**
     * Profil yang tidak dapat dipakai.
     *
     * [profile] terisi bila berkas berhasil di-decode tetapi gagal validasi,
     * sehingga layar Models masih dapat menampilkan nama, label, dan pemetaannya
     * beserta alasan penolakan. [profile] `null` berarti berkas tidak dapat
     * dibaca sama sekali.
     */
    data class Broken(
        override val fileName: String,
        val profileId: String,
        val jsonProblems: List<ProfileJsonProblem>,
        val validationProblems: List<ProfileProblem>,
        val profile: ModelProfile? = null,
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

/**
 * Hasil penghapusan profil (instruksi pengguna 27 Oktober 2026).
 *
 * `Deleted` hanya dilaporkan bila berkas profil dan berkas model yang ada sudah
 * tidak ada lagi; kegagalan penyimpanan dilaporkan apa adanya, bukan dianggap
 * berhasil.
 */
sealed interface ProfileDeleteResult {
    data class Deleted(val profileId: String, val modelFileDeleted: Boolean) : ProfileDeleteResult

    /** Tidak ada berkas profil maupun berkas model untuk `profileId` itu. */
    data class NotFound(val profileId: String) : ProfileDeleteResult

    /** Penyimpanan menolak penghapusan [fileName]; sisa berkas dilaporkan apa adanya. */
    data class StorageFailed(
        val reason: StoreProblemReason,
        val fileName: String? = null,
    ) : ProfileDeleteResult
}

/** Keadaan profil aktif; tidak pernah berupa galat bagi pemanggil. */sealed interface ActiveProfileState {
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
