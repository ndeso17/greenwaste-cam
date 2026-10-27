package id.pemilah.model

import id.pemilah.domain.waste.WasteClass

/**
 * Profil model sesuai `.work/Schema.md` §3. Nilai numerik di sini berasal dari
 * berkas profil operator, bukan konstanta aplikasi.
 */
data class ModelProfile(
    val schemaVersion: Int,
    val profileId: String,
    val name: String,
    val version: String,
    val modelFile: String,
    val labels: List<String>,
    val classMapping: Map<String, WasteClass>,
    val input: InputSpec,
    val output: OutputSpec,
    val confidenceThreshold: Double,
    val groupingEnabled: Boolean,
    val binAssignment: Map<WasteClass, String?>,
)

data class InputSpec(
    val width: Int,
    val height: Int,
    val normalization: Normalization,
    val layout: TensorLayout,
)

enum class Normalization { SCALE_0_1, SCALE_MINUS_ONE_ONE, NONE }

enum class TensorLayout { NCHW, NHWC }

data class OutputSpec(
    val format: OutputFormat,
    val boxesNormalized: Boolean,
    val hasObjectness: Boolean,
)

/**
 * Keluarga keluaran yang dikenali. [CLASSIFICATION_ONLY] adalah keluaran
 * klasifikasi `[N, C]`: satu label per gambar, tanpa kotak objek. Keluarga ini
 * **diterima** sebagai model utama prototipe (D-10, 27 Oktober 2026); kotak
 * objeknya berasal dari pemasok deteksi yang belum ada, dan kelangkaan itu
 * dicatat sebagai hambatan terbuka, bukan disamarkan sebagai implementasi.
 */
enum class OutputFormat { YOLO_V8, YOLO_V5, SSD_BOXES, CLASSIFICATION_ONLY }

sealed interface ProfileValidation {
    data object Valid : ProfileValidation
    data class Rejected(val problems: List<ProfileProblem>) : ProfileValidation
}

data class ProfileProblem(val field: String, val reason: ProfileProblemReason)

enum class ProfileProblemReason {
    UNKNOWN_SCHEMA_VERSION,
    NO_LABELS,
    DUPLICATE_LABEL,
    LABEL_NOT_MAPPED,
    MAPPING_FOR_UNKNOWN_LABEL,
    COMBINED_KALENG_BESI_LABEL,

    /**
     * Keluaran tidak menyatakan lokasi objek pada keluarga yang mensyaratkannya
     * (saat ini `SSD_BOXES` tanpa kotak ternormalisasi). Keluaran
     * `CLASSIFICATION_ONLY` **tidak** lagi memakai alasan ini (D-10).
     */
    NO_LOCATION_OUTPUT,
    INVALID_CONFIDENCE_THRESHOLD,
    INVALID_INPUT_SIZE,
    UNKNOWN_NORMALIZATION,

    /** Kelas yang disunting tidak dipetakan oleh label profil mana pun (MOD-05). */
    CLASS_NOT_IN_PROFILE,

    /** Nama bak kosong bukan berarti "belum ditetapkan"; yang benar adalah `null`. */
    INVALID_BIN_NAME,

    /**
     * Profil menunjuk berkas model yang tidak ada di penyimpanan aplikasi.
     * Model yang hilang tidak boleh dipakai: profil tanpa model tidak dapat
     * melakukan inferensi apa pun (D-09, MOD-01, NFR-03).
     */
    MODEL_FILE_MISSING,
}
