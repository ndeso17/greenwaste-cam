package id.pemilah.model

import id.pemilah.domain.waste.WasteClass

/**
 * Alur impor berkas model ONNX: baca → inspeksi → simpan (D-09, MOD-01, MOD-02,
 * SC-09).
 *
 * Aturan yang dijaga:
 * - Identitas profil lahir dari berkas model, bukan dari berkas konfigurasi
 *   terpisah: label dari metadata `names`, ukuran masukan dari bentuk tensor
 *   masukan, keluarga keluaran dari bentuk tensor keluaran ([OnnxMetadata]).
 * - Ambang keyakinan hasil impor adalah `0.0`, artinya "belum ditetapkan".
 *   Aplikasi tidak mengarang ambang pilihan operator; aktivasi model dengan
 *   ambang itu diblokir `ProfileSwitchGuard` (MOD-04).
 * - Setiap tahap yang gagal menghentikan impor dan mengembalikan alasan
 *   bertipe. Profil lama tidak diubah karena penulisan hanya terjadi pada jalur
 *   yang sudah lolos inspeksi dan validasi.
 * - Isi berkas disalin ke penyimpanan aplikasi; URI pemilih dokumen tidak
 *   disimpan karena izin bacanya tidak dijamin bertahan (NFR-03).
 * - Batas ukuran berasal dari konfigurasi yang diberikan pemanggil. Bila
 *   `maxBytes` `null`, batas belum ditetapkan sehingga pemeriksaan ukuran tidak
 *   dijalankan dan tidak ada angka batas yang dikarang di sini (B-06).
 */
class ModelImport(
    private val repository: ProfileRepository,
    private val inspector: OnnxModelInspector,
    private val maxBytes: Long? = null,
) {

    fun import(uri: String, reader: ModelFileReader): ModelImportResult {
        val displayName = reader.displayName(uri)?.trim()
        if (displayName.isNullOrEmpty()) {
            return reject(ModelImportProblemCode.MISSING_FILE_NAME, detail = uri)
        }
        if (!displayName.endsWith(ONNX_EXTENSION, ignoreCase = true)) {
            return reject(ModelImportProblemCode.NOT_ONNX_MODEL, detail = displayName)
        }
        val bytes = reader.readBytes(uri)
            ?: return reject(ModelImportProblemCode.UNREADABLE_FILE, detail = displayName)
        if (bytes.isEmpty()) {
            return reject(ModelImportProblemCode.EMPTY_FILE, detail = displayName)
        }
        if (maxBytes != null && bytes.size.toLong() > maxBytes) {
            return reject(
                ModelImportProblemCode.FILE_TOO_LARGE,
                detail = "${bytes.size} > $maxBytes",
            )
        }
        val info = when (val inspection = inspector.inspect(bytes)) {
            is OnnxInspectionResult.Rejected -> return reject(
                inspection.problem.code.toImportCode(),
                detail = inspection.problem.detail,
            )

            is OnnxInspectionResult.Inspected -> inspection.info
        }
        return store(profileOf(displayName, info), bytes)
    }

    /**
     * Bangun profil dari identitas model.
     *
     * Label dipetakan ke tujuh kelas resmi berdasarkan **nama label model**.
     * Label yang tidak cocok tetap disimpan apa adanya; validator menolaknya
     * dengan alasan per label sehingga operator melihat label mana yang tidak
     * dikenali, bukan menerima profil yang tampak sah (MOD-02).
     */
    private fun profileOf(displayName: String, info: OnnxModelInfo): ModelProfile {
        // Nama berkas sudah dipastikan berakhiran `.onnx` (tanpa peduli huruf besar).
        val baseName = displayName.dropLast(ONNX_EXTENSION.length)
        val slug = slugOf(baseName)
        val profileId = uniqueProfileId(slug)
        return ModelProfile(
            schemaVersion = ProfileValidator.SUPPORTED_SCHEMA_VERSION,
            profileId = profileId,
            // Nama tampil ikut dibedakan supaya dua model berberkas sama tidak
            // tampil identik pada layar Models.
            name = displayNameFor(baseName, slug, profileId),
            version = info.modelVersion.orEmpty(),
            modelFile = ProfileRepository.modelFileNameFor(profileId),
            labels = info.labels,
            classMapping = info.labels.mapNotNull { label ->
                officialClassOf(label)?.let { wasteClass -> label to wasteClass }
            }.toMap(),
            input = info.input,
            output = info.output,
            // Belum ditetapkan: operator yang memilih ambang, bukan aplikasi.
            confidenceThreshold = 0.0,
            groupingEnabled = false,
            binAssignment = emptyMap(),
        )
    }

    private fun store(profile: ModelProfile, modelBytes: ByteArray): ModelImportResult =
        when (val saved = repository.saveWithModel(profile, modelBytes)) {
            is ProfileWriteResult.Written -> ModelImportResult.Imported(profile, modelBytes.size.toLong())

            is ProfileWriteResult.Rejected -> ModelImportResult.Rejected(
                saved.problems.map { problem ->
                    ModelImportProblem(
                        code = ModelImportProblemCode.INVALID_PROFILE,
                        field = problem.field,
                        detail = problem.reason.name,
                    )
                },
            )

            is ProfileWriteResult.Unusable -> ModelImportResult.Rejected(
                saved.stored.validationProblems.map { problem ->
                    ModelImportProblem(
                        code = ModelImportProblemCode.INVALID_PROFILE,
                        field = problem.field,
                        detail = problem.reason.name,
                    )
                },
            )

            is ProfileWriteResult.NotFound -> ModelImportResult.Rejected(
                listOf(
                    ModelImportProblem(
                        code = ModelImportProblemCode.STORAGE_FAILED,
                        detail = saved.profileId,
                    ),
                ),
            )

            is ProfileWriteResult.StorageFailed -> ModelImportResult.Rejected(
                listOf(
                    ModelImportProblem(
                        code = ModelImportProblemCode.STORAGE_FAILED,
                        detail = saved.detail ?: saved.reason.name,
                    ),
                ),
            )
        }

    private fun reject(code: ModelImportProblemCode, detail: String?): ModelImportResult =
        ModelImportResult.Rejected(listOf(ModelImportProblem(code = code, detail = detail)))

    /**
     * `profileId` yang belum dipakai. Nama berkas yang sama tidak menimpa profil
     * lama: operator yang mengimpor dua model bernama sama tetap memiliki
     * keduanya, dan profil lama tidak berubah karena impor (MOD-01).
     */
    private fun uniqueProfileId(base: String): String {
        if (!repository.exists(base)) return base
        var counter = 2
        while (repository.exists("$base-$counter")) counter++
        return "$base-$counter"
    }

    /**
     * Nama tampil model. Impor pertama memakai nama berkas apa adanya; impor
     * berikutnya dengan nama berkas sama menambahkan akhiran pembeda supaya dua
     * kartu pada layar Models tidak tampak identik.
     */
    private fun displayNameFor(baseName: String, slug: String, profileId: String): String {
        val plain = baseName.ifBlank { profileId }
        if (profileId == slug) return plain
        val suffix = profileId.removePrefix("$slug-").ifEmpty { profileId }
        return "$plain ($suffix)"
    }

    /** Bentuk aman untuk nama berkas: huruf kecil, angka, tanda hubung, garis bawah. */
    private fun slugOf(baseName: String): String {
        val slug = baseName
            .trim()
            .lowercase()
            .map { character -> if (character.isLetterOrDigit()) character else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-{2,}"), "-")
        return slug.ifEmpty { FALLBACK_PROFILE_ID }
    }

    private fun officialClassOf(label: String): WasteClass? {
        val normalized = label.uppercase().filter { it.isLetterOrDigit() }
        return WasteClass.entries.firstOrNull { it.name == normalized }
    }

    private companion object {
        const val ONNX_EXTENSION = ".onnx"
        const val FALLBACK_PROFILE_ID = "model"
    }
}

/** Hasil impor berkas model ONNX. */
sealed interface ModelImportResult {
    /** Profil tersimpan; [sizeBytes] adalah ukuran berkas model yang dibaca. */
    data class Imported(val profile: ModelProfile, val sizeBytes: Long) : ModelImportResult

    data class Rejected(val problems: List<ModelImportProblem>) : ModelImportResult
}

/** Satu sebab impor gagal, dengan kode stabil untuk teks tampilan (D-03). */
data class ModelImportProblem(
    val code: ModelImportProblemCode,
    val field: String? = null,
    val detail: String? = null,
)

enum class ModelImportProblemCode {
    /** Pemilih dokumen tidak memberi nama berkas sehingga jenisnya tak dapat dipastikan. */
    MISSING_FILE_NAME,

    /** Nama berkas bukan `.onnx`; impor JSON konfigurasi tidak lagi didukung (D-09). */
    NOT_ONNX_MODEL,

    /** Berkas tidak dapat dibaca, misalnya URI sudah tidak berlaku. */
    UNREADABLE_FILE,

    /** Berkas kosong. */
    EMPTY_FILE,

    /** Berkas melebihi batas ukuran yang diberikan pemanggil. */
    FILE_TOO_LARGE,

    /** Berkas tidak dapat dibuka sebagai model ONNX. */
    UNREADABLE_MODEL,

    /** Model tidak menuliskan label kelas pada metadata `names`. */
    MISSING_CLASS_NAMES,

    /** Bentuk tensor masukan bukan gambar empat dimensi yang didukung. */
    UNSUPPORTED_INPUT_SHAPE,

    /** Bentuk tensor keluaran tidak cocok dengan jumlah kelas model. */
    UNSUPPORTED_OUTPUT_SHAPE,

    /** Model terbaca tetapi profilnya ditolak validator, misalnya label tak dikenal. */
    INVALID_PROFILE,

    /** Penyimpanan menolak tulisan; profil lama tetap utuh. */
    STORAGE_FAILED,
}

private fun OnnxInspectionCode.toImportCode(): ModelImportProblemCode = when (this) {
    OnnxInspectionCode.EMPTY_FILE -> ModelImportProblemCode.EMPTY_FILE
    OnnxInspectionCode.FILE_TOO_LARGE -> ModelImportProblemCode.FILE_TOO_LARGE
    OnnxInspectionCode.UNREADABLE_MODEL -> ModelImportProblemCode.UNREADABLE_MODEL
    OnnxInspectionCode.MISSING_CLASS_NAMES -> ModelImportProblemCode.MISSING_CLASS_NAMES
    OnnxInspectionCode.UNSUPPORTED_INPUT_SHAPE -> ModelImportProblemCode.UNSUPPORTED_INPUT_SHAPE
    OnnxInspectionCode.UNSUPPORTED_OUTPUT_SHAPE -> ModelImportProblemCode.UNSUPPORTED_OUTPUT_SHAPE
}
