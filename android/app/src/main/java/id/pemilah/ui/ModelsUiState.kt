package id.pemilah.ui

import id.pemilah.domain.waste.WasteClass
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.ModelProfile
import id.pemilah.model.ProfileJsonProblem
import id.pemilah.model.ProfileJsonProblemReason
import id.pemilah.model.ProfileProblem
import id.pemilah.model.ProfileProblemReason
import id.pemilah.model.StoredProfile

/**
 * Seluruh data yang dibutuhkan layar Models (UI-02, MOD-01–MOD-06).
 *
 * Semua kolom berasal dari pemilik state sebenarnya (`ProfileRepository`);
 * tidak ada profil, label, atau angka contoh yang lahir di lapisan tampilan
 * (`Design.md` §6). Daftar kosong berarti belum ada profil, bukan daftar contoh.
 */
data class ModelsUiState(
    val loading: Boolean = false,
    val rows: List<ModelsProfileRow> = emptyList(),
    val activeProfileId: String? = null,
    val activeAbsentReason: ActiveProfileAbsentReason? = null,
) {
    /** Belum ada profil tersimpan dan tidak sedang memuat. */
    val isEmpty: Boolean get() = !loading && rows.isEmpty()

    companion object {
        /**
         * Proyeksikan daftar profil tersimpan dan keadaan profil aktif menjadi
         * state tampilan. Profil rusak tetap muncul dengan alasannya
         * (`Rules.md` §2.2).
         */
        fun from(
            stored: List<StoredProfile>,
            active: ActiveProfileState,
            loading: Boolean = false,
        ): ModelsUiState {
            // Penanda aktif hanya untuk profil yang benar-benar aktif; `None`
            // tidak pernah menandai baris mana pun karena tidak ada profil aktif.
            val activeId = (active as? ActiveProfileState.Selected)?.profile?.profileId
            return ModelsUiState(
                loading = loading,
                rows = stored.map { row(it, activeId) },
                activeProfileId = activeId,
                activeAbsentReason = (active as? ActiveProfileState.None)?.reason,
            )
        }

        private fun row(stored: StoredProfile, activeId: String?): ModelsProfileRow = when (stored) {
            is StoredProfile.Usable -> ModelsProfileRow(
                profileId = stored.profile.profileId,
                fileName = stored.fileName,
                name = stored.profile.name,
                version = stored.profile.version,
                status = ModelsProfileStatus.VALID,
                problems = emptyList(),
                labels = labelsOf(stored.profile),
                isActive = stored.profile.profileId == activeId,
            )

            is StoredProfile.Broken -> ModelsProfileRow(
                profileId = stored.profileId,
                fileName = stored.fileName,
                name = stored.profile?.name,
                version = stored.profile?.version,
                status = if (stored.profile == null) {
                    ModelsProfileStatus.UNREADABLE
                } else {
                    ModelsProfileStatus.INVALID
                },
                problems = stored.jsonProblems.map { it.toUi() } +
                    stored.validationProblems.map { it.toUi() },
                labels = stored.profile?.let { labelsOf(it) } ?: emptyList(),
                isActive = stored.profileId == activeId,
            )
        }

        private fun labelsOf(profile: ModelProfile): List<ModelsLabelUi> = profile.labels.map { label ->
            val wasteClass = profile.classMapping[label]
            ModelsLabelUi(
                label = label,
                wasteClass = wasteClass,
                bin = wasteClass?.let { profile.binAssignment[it] },
            )
        }
    }
}

/** Satu baris daftar profil (UI-02, `ProfileCard`). */
data class ModelsProfileRow(
    val profileId: String,
    val fileName: String,
    /** `null` bila berkas tidak dapat dibaca sama sekali. */
    val name: String?,
    val version: String?,
    val status: ModelsProfileStatus,
    val problems: List<ModelsProblemUi>,
    val labels: List<ModelsLabelUi>,
    val isActive: Boolean,
)

/** Status validasi satu profil; ditampilkan sebagai teks + ikon, bukan warna saja. */
enum class ModelsProfileStatus {
    /** Lolos `ProfileValidator`. */
    VALID,

    /** Terbaca sebagai JSON tetapi ditolak validator. */
    INVALID,

    /** Berkas tidak dapat dibaca menjadi profil. */
    UNREADABLE,
}

/**
 * Kode alasan tampilan. Kode ini milik lapisan tampilan supaya `strings.xml`
 * tetap sumber seluruh teks (D-03) dan enum domain tidak perlu tahu teks UI.
 */
enum class ModelsProblemCode {
    JSON_INVALID,
    MISSING_FIELD,
    UNKNOWN_SCHEMA_VERSION,
    INVALID_VALUE,
    UNKNOWN_WASTE_CLASS,
    NO_LABELS,
    DUPLICATE_LABEL,
    LABEL_NOT_MAPPED,
    MAPPING_FOR_UNKNOWN_LABEL,
    COMBINED_KALENG_BESI_LABEL,
    NO_LOCATION_OUTPUT,
    INVALID_CONFIDENCE_THRESHOLD,
    INVALID_INPUT_SIZE,
    UNKNOWN_NORMALIZATION,
    CLASS_NOT_IN_PROFILE,
    INVALID_BIN_NAME,
}

/** Satu alasan penolakan yang dapat ditampilkan pada baris profil. */
data class ModelsProblemUi(
    val field: String,
    val code: ModelsProblemCode,
    val detail: String? = null,
)

/** Satu label profil beserta kelas resmi dan bak tujuannya (MOD-02, Schema §3). */
data class ModelsLabelUi(
    val label: String,
    /** `null` berarti label belum dipetakan ke salah satu kelas resmi. */
    val wasteClass: WasteClass?,
    /** `null` berarti kelas belum ditetapkan ke bak (bukan nilai bawaan). */
    val bin: String?,
)

internal fun ProfileJsonProblem.toUi(): ModelsProblemUi = ModelsProblemUi(
    field = field,
    code = when (reason) {
        ProfileJsonProblemReason.INVALID_JSON -> ModelsProblemCode.JSON_INVALID
        ProfileJsonProblemReason.MISSING_FIELD -> ModelsProblemCode.MISSING_FIELD
        ProfileJsonProblemReason.UNKNOWN_SCHEMA_VERSION -> ModelsProblemCode.UNKNOWN_SCHEMA_VERSION
        ProfileJsonProblemReason.INVALID_VALUE -> ModelsProblemCode.INVALID_VALUE
        ProfileJsonProblemReason.UNKNOWN_WASTE_CLASS -> ModelsProblemCode.UNKNOWN_WASTE_CLASS
    },
    detail = detail,
)

internal fun ProfileProblem.toUi(): ModelsProblemUi = ModelsProblemUi(
    field = field,
    code = when (reason) {
        ProfileProblemReason.UNKNOWN_SCHEMA_VERSION -> ModelsProblemCode.UNKNOWN_SCHEMA_VERSION
        ProfileProblemReason.NO_LABELS -> ModelsProblemCode.NO_LABELS
        ProfileProblemReason.DUPLICATE_LABEL -> ModelsProblemCode.DUPLICATE_LABEL
        ProfileProblemReason.LABEL_NOT_MAPPED -> ModelsProblemCode.LABEL_NOT_MAPPED
        ProfileProblemReason.MAPPING_FOR_UNKNOWN_LABEL -> ModelsProblemCode.MAPPING_FOR_UNKNOWN_LABEL
        ProfileProblemReason.COMBINED_KALENG_BESI_LABEL -> ModelsProblemCode.COMBINED_KALENG_BESI_LABEL
        ProfileProblemReason.NO_LOCATION_OUTPUT -> ModelsProblemCode.NO_LOCATION_OUTPUT
        ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD ->
            ModelsProblemCode.INVALID_CONFIDENCE_THRESHOLD

        ProfileProblemReason.INVALID_INPUT_SIZE -> ModelsProblemCode.INVALID_INPUT_SIZE
        ProfileProblemReason.UNKNOWN_NORMALIZATION -> ModelsProblemCode.UNKNOWN_NORMALIZATION
        ProfileProblemReason.CLASS_NOT_IN_PROFILE -> ModelsProblemCode.CLASS_NOT_IN_PROFILE
        ProfileProblemReason.INVALID_BIN_NAME -> ModelsProblemCode.INVALID_BIN_NAME
    },
)