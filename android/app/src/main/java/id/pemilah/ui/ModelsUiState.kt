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
    /**
     * Draf teks ambang keyakinan per `profileId`. Nilai awal diambil dari profil
     * tersimpan, bukan angka bawaan aplikasi; selama draf ada, teks operator yang
     * ditampilkan (MOD-04).
     */
    val thresholdDrafts: Map<String, String> = emptyMap(),
    /** Alasan penolakan per `profileId` yang ditampilkan pada field ambang. */
    val thresholdProblems: Map<String, List<ModelsProblemUi>> = emptyMap(),
) {
    /** Belum ada profil tersimpan dan tidak sedang memuat. */
    val isEmpty: Boolean get() = !loading && rows.isEmpty()

    /** Teks ambang yang ditampilkan: draf operator bila ada, jika tidak nilai tersimpan. */
    fun thresholdTextOf(profileId: String): String? =
        thresholdDrafts[profileId] ?: rows.firstOrNull { it.profileId == profileId }?.thresholdText

    /**
     * Simpan draf teks ambang operator. Draf tidak mengubah profil tersimpan
     * sampai aksi terapkan dijalankan (MOD-04).
     */
    fun withThresholdDraft(profileId: String, text: String): ModelsUiState =
        copy(thresholdDrafts = thresholdDrafts + (profileId to text))

    /** Pasang alasan penolakan ambang pada satu profil, atau bersihkan bila kosong. */
    fun withThresholdProblems(profileId: String, problems: List<ModelsProblemUi>): ModelsUiState =
        copy(
            thresholdProblems = if (problems.isEmpty()) {
                thresholdProblems - profileId
            } else {
                thresholdProblems + (profileId to problems)
            },
        )

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
                thresholdText = ModelsNumbers.fractionToText(stored.profile.confidenceThreshold),
                thresholdSet = stored.profile.confidenceThreshold > 0.0,
                classCount = stored.profile.labels.size,
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
                thresholdText = stored.profile?.confidenceThreshold
                    ?.let { ModelsNumbers.fractionToText(it) },
                thresholdSet = (stored.profile?.confidenceThreshold ?: 0.0) > 0.0,
                classCount = stored.profile?.labels?.size ?: 0,
            )
        }

        /**
         * Label model beserta kelas resmi hasil pencocokan nama (D-09, MOD-02).
         *
         * Tidak ada bak pada baris label: pemetaan kelas ke bak tidak lagi
         * ditampilkan layar Models (celah UI dicatat di `.work/FINDINGS.md`).
         */
        private fun labelsOf(profile: ModelProfile): List<ModelsLabelUi> = profile.labels.map { label ->
            ModelsLabelUi(label = label, wasteClass = profile.classMapping[label])
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
    /** Ambang tersimpan sebagai teks; titik desimal, tanpa pembulatan. */
    val thresholdText: String?,
    /** `false` berarti ambang belum ditetapkan (`0.0`) sehingga aktivasi diblokir (MOD-04). */
    val thresholdSet: Boolean,
    /** Jumlah kelas yang dibaca dari model (D-09, UI-02). */
    val classCount: Int,
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

    /** Profil menunjuk berkas model yang tidak ada di penyimpanan aplikasi (D-09). */
    MODEL_FILE_MISSING,
}

/** Satu alasan penolakan yang dapat ditampilkan pada baris profil. */
data class ModelsProblemUi(
    val field: String,
    val code: ModelsProblemCode,
    val detail: String? = null,
)

/**
 * Satu label yang tertulis di dalam model beserta kelas resmi hasil pencocokan
 * nama (D-09, MOD-02). Tidak ada bak di sini: layar Models tidak lagi
 * menampilkan alokasi bak.
 */
data class ModelsLabelUi(
    val label: String,
    /** `null` berarti nama label model belum dipetakan ke salah satu kelas resmi. */
    val wasteClass: WasteClass?,
)

/**
 * Hasil aksi hapus yang ditampilkan di layar Models (instruksi pengguna
 * 27 Oktober 2026). Teks tetap milik `strings.xml`; tipe ini hanya membawa data.
 */
sealed interface ModelsDeleteUi {
    data class Deleted(val name: String) : ModelsDeleteUi
    data class Failed(val detail: String) : ModelsDeleteUi
}

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
        ProfileProblemReason.MODEL_FILE_MISSING -> ModelsProblemCode.MODEL_FILE_MISSING
    },
)

/**
 * Angka tampilan untuk layar Models (MOD-04).
 *
 * Nilai ambang ditampilkan apa adanya: teks operator tidak dibulatkan diam-diam,
 * dan bentuk persen dihitung dari nilai tersimpan tanpa mengubah nilainya.
 * Pemisah desimal koma diterima karena operator Indonesia mengetik "0,45".
 */
internal object ModelsNumbers {

    /** Teks Pecahan titik-desimal dari nilai tersimpan, tanpa pembulatan. */
    fun fractionToText(value: Double): String = java.math.BigDecimal.valueOf(value)
        .stripTrailingZeros()
        .toPlainString()

    /** Bentuk persen dari nilai tersimpan, misalnya `0.455` → `45.5%`. */
    fun toPercentText(value: Double): String =
        java.math.BigDecimal.valueOf(value).movePointRight(2).stripTrailingZeros().toPlainString() + "%"

    /**
     * Bentuk persen untuk nilai keyakinan inferensi (tipe `Float`).
     *
     * `Float` membawa derau biner (`0.95609998…`); teks operator dibulatkan ke
     * persen bulat supaya tidak menampilkan angka palsu berpresisi tinggi.
     */
    fun floatToPercentText(value: Float): String =
        java.math.BigDecimal.valueOf((value * 100f).toDouble())
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toPlainString() + "%"

    /** Baca teks operator menjadi pecahan; `null` bila bukan bilangan. */
    fun parseFraction(text: String): Double? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
}