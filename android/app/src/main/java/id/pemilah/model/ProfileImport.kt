package id.pemilah.model

/**
 * Pembaca isi berkas profil yang dipilih operator.
 *
 * Abstraksi ini memisahkan pemilih dokumen Android dari logika impor supaya
 * seluruh jalur impor dapat diuji tanpa perangkat (`Styleguide.md` §4).
 * Implementasi nyata memakai `ContentResolver` pada
 * `id.pemilah.storage.ContentProfileReader`; pembacaan dilakukan di luar
 * Composable.
 */
interface ProfileFileReader {
    /** Isi berkas, atau `null` bila berkas tidak dapat dibaca. */
    fun read(uri: String): String?
}

/**
 * Alur impor berkas profil: baca → decode → validasi → simpan (MOD-01, MOD-02,
 * SC-09).
 *
 * Aturan yang dijaga:
 * - Setiap tahap yang gagal menghentikan impor dan mengembalikan alasan
 *   spesifik. Tidak ada profil lama yang dihapus atau ditimpa oleh impor yang
 *   gagal, karena penulisan hanya terjadi pada jalur yang sudah lolos validasi.
 * - Isi berkas dibaca dan disimpan sebagai salinan di penyimpanan aplikasi,
 *   bukan disimpan sebagai URI; URI pemilih dokumen tidak dijamin dapat dibaca
 *   lagi setelah proses aplikasi dibangun ulang.
 * - Batas ukuran berkas berasal dari konfigurasi yang diberikan pemanggil.
 *   Bila `maxBytes` `null`, batas belum ditetapkan sehingga pemeriksaan ukuran
 *   tidak dijalankan dan tidak ada angka batas yang dikarang di sini
 *   (menunggu B-06).
 */
class ProfileImport(
    private val repository: ProfileRepository,
    private val maxBytes: Long? = null,
) {

    fun import(uri: String, reader: ProfileFileReader): ProfileImportResult {
        val text = reader.read(uri)
            ?: return reject(ProfileImportProblemCode.UNREADABLE_FILE, detail = uri)
        if (text.isBlank()) {
            return reject(ProfileImportProblemCode.EMPTY_FILE, detail = uri)
        }
        val sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong()
        if (maxBytes != null && sizeBytes > maxBytes) {
            return reject(
                ProfileImportProblemCode.FILE_TOO_LARGE,
                detail = "$sizeBytes > $maxBytes",
            )
        }
        return when (val decoded = ProfileJson.decode(text)) {
            is ProfileDecodeResult.Malformed -> ProfileImportResult.Rejected(
                decoded.problems.map { problem ->
                    ProfileImportProblem(
                        code = ProfileImportProblemCode.INVALID_JSON,
                        field = problem.field,
                        detail = problem.detail,
                    )
                },
            )

            is ProfileDecodeResult.Decoded -> store(decoded.profile, sizeBytes)
        }
    }

    private fun store(profile: ModelProfile, sizeBytes: Long): ProfileImportResult =
        when (val saved = repository.save(profile)) {
            is ProfileWriteResult.Written -> ProfileImportResult.Imported(profile, sizeBytes)

            is ProfileWriteResult.Rejected -> ProfileImportResult.Rejected(
                saved.problems.map { problem ->
                    ProfileImportProblem(
                        code = ProfileImportProblemCode.INVALID_PROFILE,
                        field = problem.field,
                        detail = problem.reason.name,
                    )
                },
            )

            is ProfileWriteResult.Unusable -> ProfileImportResult.Rejected(
                saved.stored.validationProblems.map { problem ->
                    ProfileImportProblem(
                        code = ProfileImportProblemCode.INVALID_PROFILE,
                        field = problem.field,
                        detail = problem.reason.name,
                    )
                },
            )

            is ProfileWriteResult.NotFound -> ProfileImportResult.Rejected(
                listOf(
                    ProfileImportProblem(
                        code = ProfileImportProblemCode.STORAGE_FAILED,
                        detail = saved.profileId,
                    ),
                ),
            )

            is ProfileWriteResult.StorageFailed -> ProfileImportResult.Rejected(
                listOf(
                    ProfileImportProblem(
                        code = ProfileImportProblemCode.STORAGE_FAILED,
                        detail = saved.detail ?: saved.reason.name,
                    ),
                ),
            )
        }

    private fun reject(code: ProfileImportProblemCode, detail: String?): ProfileImportResult =
        ProfileImportResult.Rejected(listOf(ProfileImportProblem(code = code, detail = detail)))
}

/** Hasil impor berkas profil. */
sealed interface ProfileImportResult {
    /** Profil tersimpan; [sizeBytes] adalah ukuran berkas yang dibaca. */
    data class Imported(val profile: ModelProfile, val sizeBytes: Long) : ProfileImportResult

    data class Rejected(val problems: List<ProfileImportProblem>) : ProfileImportResult
}

/** Satu sebab impor gagal, dengan kode stabil untuk teks tampilan (D-03). */
data class ProfileImportProblem(
    val code: ProfileImportProblemCode,
    val field: String? = null,
    val detail: String? = null,
)

enum class ProfileImportProblemCode {
    /** Berkas tidak dapat dibaca, misalnya URI sudah tidak berlaku. */
    UNREADABLE_FILE,

    /** Berkas kosong atau hanya berisi spasi. */
    EMPTY_FILE,

    /** Berkas melebihi batas ukuran yang diberikan pemanggil. */
    FILE_TOO_LARGE,

    /** Isi berkas bukan JSON profil yang sah. */
    INVALID_JSON,

    /** Berkas terbaca tetapi profil ditolak validator. */
    INVALID_PROFILE,

    /** Penyimpanan menolak tulisan; profil lama tetap utuh. */
    STORAGE_FAILED,
}