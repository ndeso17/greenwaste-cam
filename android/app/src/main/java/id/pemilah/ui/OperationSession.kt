package id.pemilah.ui

import id.pemilah.domain.operation.MachineState
import id.pemilah.inference.ArgbFrame
import id.pemilah.inference.Classifier
import id.pemilah.inference.ClassifierFailure
import id.pemilah.inference.ClassifierFailureCode
import id.pemilah.inference.ClassifierOutcome
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.ModelProfile

/**
 * Hasil klasifikasi yang ditampilkan panel uji klasifikasi (D-13).
 *
 * `Profile` membawa nama model yang benar-benar dipakai supaya operator tahu
 * hasil ini milik model mana, bukan hasil tanpa asal.
 */
sealed interface ClassificationUi {
    /** Belum ada frame yang berhasil diklasifikasi sejak pemantauan dimulai. */
    data object Waiting : ClassificationUi

    data class Result(
        val profileName: String,
        val label: String,
        val probability: Float,
        val threshold: Double,
        val runnerUp: List<Pair<String, Float>>,
    ) : ClassificationUi {
        /** Probabilitas di bawah ambang keyakinan profil (MOD-04, D-13). */
        val belowThreshold: Boolean get() = probability < threshold
    }

    data class Failed(val profileName: String, val failure: ClassifierFailure) : ClassificationUi
}

/**
 * Pemilik state layar Operasi (D-13, `Architecture.md` §4).
 *
 * Aturan yang dijaga:
 * - Status berjalan adalah satu-satunya sumber tombol Mulai/Hentikan; guard
 *   [StartGuardProjection] tetap yang memutuskan boleh-tidaknya mulai.
 * - Klasifikasi hanya berjalan saat status berjalan aktif dan ada profil model
 *   aktif yang sah; tanpa itu [ClassificationUi] kembali ke
 *   [ClassificationUi.Waiting] dan tidak ada nilai yang dipertahankan.
 * - Frame diproses pada thread pemanggil (analyzer kamera), bukan thread UI;
 *   hasil terakhir disimpan sebagai nilai tunggal tanpa antrean.
 */
class OperationSession(
    private val classifier: Classifier,
    private val activeProfile: () -> ActiveProfileState,
    private val machineState: () -> MachineState = { MachineState.Stopped },
    private val hasActiveTarget: () -> Boolean = { false },
) {
    var isRunning: Boolean = false
        private set

    var classification: ClassificationUi = ClassificationUi.Waiting
        private set

    /**
     * Status kamera terakhir yang dilaporkan [id.pemilah.camera.CameraPreview].
     * Disimpan di sini karena guard mulai pemantauan memeriksanya; bukan nilai
     * contoh, melainkan laporan pemilik kamera (D-11).
     */
    var cameraStatus: id.pemilah.camera.CameraStatus = id.pemilah.camera.CameraStatus.Starting
        private set

    /** Notifikasi setiap kali [isRunning] atau [classification] berubah. */
    var onChange: () -> Unit = {}

    fun updateCameraStatus(status: id.pemilah.camera.CameraStatus) {
        if (cameraStatus == status) return
        cameraStatus = status
        onChange()
    }

    /** Model aktif yang sah, atau `null` bila belum dipilih/rusak. */
    fun runningProfile(): ModelProfile? = when (val state = activeProfile()) {
        is ActiveProfileState.Selected -> state.profile
        is ActiveProfileState.None -> null
    }

    /**
     * Mulai pemantauan; `false` bila guard menolak atau sudah berjalan.
     *
     * State guard dibangun dari keadaan yang benar-benar diketahui pemilik state
     * ini: status kamera dari [updateCameraStatus] dan nama profil dari repositori.
     * Kalibrasi, konfigurasi mesin, pemetaan bak, dan koneksi controller tidak
     * ikut diperiksa pada pemantauan (D-11).
     */
    fun start(): Boolean {
        if (isRunning) return false
        val profile = runningProfile() ?: return false
        val state = OperationUiState(
            stage = OperationStage.MONITORING,
            cameraStatus = cameraStatus,
            profileNameAndVersion = profileLabel(profile),
            isRunning = false,
        )
        if (!StartGuardProjection.canStart(state)) return false
        isRunning = true
        classification = ClassificationUi.Waiting
        onChange()
        return true
    }

    private fun profileLabel(profile: ModelProfile): String =
        profile.version.takeIf { it.isNotBlank() }?.let { "${profile.name} $it" } ?: profile.name

    fun stop() {
        if (!isRunning) return
        isRunning = false
        classification = ClassificationUi.Waiting
        onChange()
    }

    /**
     * Klasifikasi satu frame kamera. Dipanggil analyzer; hasil diabaikan bila
     * pemantauan sudah berhenti di tengah inferensi.
     */
    fun onFrame(frame: ArgbFrame) {
        if (!isRunning) return
        val profile = runningProfile() ?: return
        val outcome = classifier.classify(profile, frame)
        if (!isRunning) return
        classification = when (outcome) {
            is ClassifierOutcome.Classified -> {
                val label = outcome.result.topLabel
                if (label == null) {
                    ClassificationUi.Failed(
                        profileName = profile.name,
                        failure = ClassifierFailure(
                            ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                            "model tidak mengeluarkan kelas",
                        ),
                    )
                } else {
                    ClassificationUi.Result(
                        profileName = profile.name,
                        label = label,
                        probability = outcome.result.topProbability,
                        threshold = profile.confidenceThreshold,
                        runnerUp = outcome.result.ranked(4).drop(1),
                    )
                }
            }

            is ClassifierOutcome.Failed -> ClassificationUi.Failed(profile.name, outcome.failure)
        }
        onChange()
    }

    /** Keadaan yang harus berhenti saat profil aktif berubah atau hilang. */
    fun onActiveProfileChanged() {
        if (isRunning && runningProfile() == null) stop()
    }

    companion object {
        /** Pesan Mesin yang dipakai guard saat Mulai ditekan. */
        fun machineState(): MachineState = MachineState.Stopped

        /** Tahap 3 belum memiliki target aktif; nilai ini eksplisit, bukan tebakan. */
        fun hasActiveTarget(): Boolean = false
    }
}
