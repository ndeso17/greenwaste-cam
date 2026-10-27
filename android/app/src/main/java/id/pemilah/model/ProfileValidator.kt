package id.pemilah.model

import id.pemilah.domain.waste.WasteClass

/**
 * Validasi kompatibilitas profil model (MOD-02, SC-09) sesuai `.work/Schema.md` §3.
 *
 * Dua aturan berasal dari koreksi kelas 24 Oktober 2026 (PRD `Camera/.work/PRD.md`
 * §6.2): profil yang kelasnya tidak dapat dipetakan ke tujuh kelas resmi ditolak,
 * dan label gabungan `Kaleng`+`Besi` ditolak karena pemisahan bak tidak mungkin.
 *
 * Yang **tidak** diperiksa di sini: jumlah label terhadap tensor model, dan
 * ukuran input terhadap tensor. Keduanya membutuhkan berkas model dan menunggu
 * B-02; pemeriksaan itu menjadi tanggung jawab pemuat model (WP-15), bukan
 * validator metadata ini.
 *
 * Ambang keyakinan `0.0` **sah** di sini karena berarti "belum ditetapkan"
 * (D-09): model yang baru diimpor belum punya ambang pilihan operator. Yang
 * dilarang adalah nilai negatif atau di atas `1.0`. Aktivasi model dengan ambang
 * belum ditetapkan diblokir `ProfileSwitchGuard`, bukan validator, supaya model
 * tetap dapat tersimpan dan diperiksa operator (MOD-04).
 */
object ProfileValidator {

    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    private val COMBINED_LABEL_PATTERNS = listOf(
        Regex("kaleng[\\s_+\\-]*besi", RegexOption.IGNORE_CASE),
        Regex("besi[\\s_+\\-]*kaleng", RegexOption.IGNORE_CASE),
        // Label `Logam_metal` pada model lama menggabungkan kaleng dan besi
        // (PRD `.work/PRD.md` §5), sehingga tidak dapat memenuhi pemisahan bak.
        Regex("^logam", RegexOption.IGNORE_CASE),
    )

    fun validate(profile: ModelProfile): ProfileValidation {
        val problems = buildList {
            if (profile.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                add(ProfileProblem("schemaVersion", ProfileProblemReason.UNKNOWN_SCHEMA_VERSION))
            }
            addAll(labelProblems(profile))
            addAll(outputProblems(profile.output))
            if (!(profile.confidenceThreshold >= 0.0 && profile.confidenceThreshold <= 1.0)) {
                add(ProfileProblem("confidenceThreshold", ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD))
            }
            if (profile.input.width <= 0 || profile.input.height <= 0) {
                add(ProfileProblem("input", ProfileProblemReason.INVALID_INPUT_SIZE))
            }
        }
        return if (problems.isEmpty()) ProfileValidation.Valid else ProfileValidation.Rejected(problems)
    }

    private fun labelProblems(profile: ModelProfile): List<ProfileProblem> = buildList {
        if (profile.labels.isEmpty()) {
            add(ProfileProblem("labels", ProfileProblemReason.NO_LABELS))
            return@buildList
        }
        if (profile.labels.distinct().size != profile.labels.size) {
            add(ProfileProblem("labels", ProfileProblemReason.DUPLICATE_LABEL))
        }
        profile.labels.forEach { label ->
            val mapped = profile.classMapping[label]
            if (mapped == null) {
                add(ProfileProblem("classMapping.$label", ProfileProblemReason.LABEL_NOT_MAPPED))
            }
            if (isCombinedKalengBesiLabel(label)) {
                add(ProfileProblem("labels.$label", ProfileProblemReason.COMBINED_KALENG_BESI_LABEL))
            }
        }
        profile.classMapping.keys
            .filter { it !in profile.labels }
            .forEach { label ->
                add(ProfileProblem("classMapping.$label", ProfileProblemReason.MAPPING_FOR_UNKNOWN_LABEL))
            }
    }

    /**
     * Label yang menggabungkan kaleng dan besi. Pemeriksaan berbasis nama label
     * karena kontrak model (B-02) belum menetapkan penanda gabungan yang eksplisit.
     * Keterbatasan ini dicatat pada Log WP-14.
     */
    fun isCombinedKalengBesiLabel(label: String): Boolean =
        COMBINED_LABEL_PATTERNS.any { it.containsMatchIn(label.trim()) }

    private fun outputProblems(output: OutputSpec): List<ProfileProblem> = buildList {
        // Keluaran klasifikasi diterima (D-10): model utama prototipe adalah
        // ResNet18 klasifikasi tujuh kelas. Syarat "menghasilkan lokasi objek"
        // berpindah ke pemasok kotak objek (deteksi), yang belum ada; tanpa
        // pemasok itu klasifikasi tidak dapat menghasilkan kandidat. Karena itu
        // profil klasifikasi **bukan** lagi ditolak di sini, dan kelangkaan
        // kotak objeknya dicatat sebagai hambatan terbuka, bukan disamarkan.
        if (output.format == OutputFormat.SSD_BOXES && !output.boxesNormalized) {
            // Bentuk keluaran SSD dengan kotak tak ternormalisasi belum memiliki
            // kontrak decoding (B-02); profil seperti itu belum dapat dipakai.
            add(ProfileProblem("output.boxesNormalized", ProfileProblemReason.NO_LOCATION_OUTPUT))
        }
    }

    /** Kelas resmi yang tercakup profil; kelas aktif tanpa cakupan memblokir start. */
    fun coveredClasses(profile: ModelProfile): Set<WasteClass> =
        profile.labels.mapNotNull { profile.classMapping[it] }.toSet()
}
