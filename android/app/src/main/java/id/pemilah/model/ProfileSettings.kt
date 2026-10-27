package id.pemilah.model

import id.pemilah.domain.waste.WasteClass

/**
 * Penyuntingan pengaturan profil model: ambang keyakinan, sakelar kelompok, dan
 * pemetaan kelas ke bak (MOD-04, MOD-05, MOD-06).
 *
 * Aturan yang dijaga:
 * - Semua fungsi murni: profil masukan tidak diubah. Penolakan mengembalikan
 *   [ProfileEditResult.Rejected] sehingga profil lama tetap persis seperti
 *   semula (`Rules.md` §2.2).
 * - Setiap hasil suntingan dijalankan ulang melalui [ProfileValidator]; profil
 *   yang menjadi tidak sah karena suntingan tidak pernah dikembalikan sebagai
 *   hasil yang dapat disimpan.
 * - Nama bak tidak divalidasi terhadap daftar bak sah: alokasi kelas ke bak
 *   menunggu B-15/B-16 dan angka bak tidak boleh dikarang di lapisan ini.
 *   Yang dijaga hanya bentuknya — `null` berarti "belum ditetapkan", sedangkan
 *   teks kosong ditolak supaya tidak menyamar sebagai penetapan.
 * - Sakelar kelompok hanya menyimpan pilihan operator. Perilaku pengelompokan
 *   sendiri menunggu B-07, jadi tidak ada keputusan pengelompokan di sini
 *   (MOD-06).
 */
object ProfileSettings {

    /** Ambang keyakinan sah: lebih besar dari 0.0 dan paling besar 1.0 (MOD-04). */
    fun withConfidenceThreshold(profile: ModelProfile, value: Double): ProfileEditResult {
        if (!value.isFinite() || value <= 0.0 || value > 1.0) {
            return ProfileEditResult.Rejected(
                listOf(
                    ProfileProblem(
                        field = "confidenceThreshold",
                        reason = ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD,
                    ),
                ),
            )
        }
        return validated(profile.copy(confidenceThreshold = value))
    }

    /** Sakelar pengelompokan tersimpan per profil; tidak mengubah aturan target. */
    fun withGroupingEnabled(profile: ModelProfile, enabled: Boolean): ProfileEditResult =
        validated(profile.copy(groupingEnabled = enabled))

    /**
     * Tetapkan atau kosongkan bak untuk satu kelas resmi.
     *
     * [bin] `null` berarti "belum ditetapkan" dan tidak diganti nilai bawaan.
     * Kelas harus sudah dipetakan oleh salah satu label profil; kelas resmi yang
     * tidak dipakai profil ini ditolak supaya daftar bak tidak memuat kelas yang
     * tidak akan pernah terdeteksi (MOD-05).
     */
    fun withBinAssignment(
        profile: ModelProfile,
        wasteClass: WasteClass,
        bin: String?,
    ): ProfileEditResult {
        if (wasteClass !in profile.classMapping.values) {
            return ProfileEditResult.Rejected(
                listOf(
                    ProfileProblem(
                        field = "binAssignment.${wasteClass.name}",
                        reason = ProfileProblemReason.CLASS_NOT_IN_PROFILE,
                    ),
                ),
            )
        }
        if (bin != null && bin.isBlank()) {
            return ProfileEditResult.Rejected(
                listOf(
                    ProfileProblem(
                        field = "binAssignment.${wasteClass.name}",
                        reason = ProfileProblemReason.INVALID_BIN_NAME,
                    ),
                ),
            )
        }
        return validated(
            profile.copy(binAssignment = profile.binAssignment + (wasteClass to bin)),
        )
    }

    /**
     * Kelas yang dapat dipilih untuk pemetaan bak: hanya kelas resmi yang
     * dipetakan profil, urut nama enum agar tampilan stabil (MOD-05).
     */
    fun assignableClasses(profile: ModelProfile): List<WasteClass> =
        WasteClass.entries.filter { it in profile.classMapping.values }

    private fun validated(candidate: ModelProfile): ProfileEditResult =
        when (val validation = ProfileValidator.validate(candidate)) {
            is ProfileValidation.Valid -> ProfileEditResult.Edited(candidate)
            is ProfileValidation.Rejected -> ProfileEditResult.Rejected(validation.problems)
        }
}

/** Hasil suntingan profil; penolakan tidak pernah mengubah profil masukan. */
sealed interface ProfileEditResult {
    data class Edited(val profile: ModelProfile) : ProfileEditResult
    data class Rejected(val problems: List<ProfileProblem>) : ProfileEditResult
}
