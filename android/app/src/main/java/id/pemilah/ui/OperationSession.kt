package id.pemilah.ui

import id.pemilah.domain.operation.MachineState
import id.pemilah.inference.ArgbFrame
import id.pemilah.inference.Classifier
import id.pemilah.inference.ClassifierFailure
import id.pemilah.inference.ClassifierFailureCode
import id.pemilah.inference.ClassifierOutcome
import id.pemilah.inference.FrameTiles
import id.pemilah.inference.TileDetection
import id.pemilah.inference.TileDetectionFilter
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
        /** Jumlah petak yang diklasifikasi pada frame ini. */
        val tilesChecked: Int,
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
     * Kotak overlay hasil klasifikasi petak (D-14): batas petak yang lolos ambang,
     * bukan tepi objek. Kosong berarti tidak ada petak yang meyakinkan.
     */
    var detections: List<TileDetection> = emptyList()
        private set

    /** Waktu (epoch ms) frame terakhir yang menghasilkan [detections]. */
    var detectionsAtMillis: Long = 0L
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
        detections = emptyList()
        onChange()
        return true
    }

    private fun profileLabel(profile: ModelProfile): String =
        profile.version.takeIf { it.isNotBlank() }?.let { "${profile.name} $it" } ?: profile.name

    fun stop() {
        if (!isRunning) return
        isRunning = false
        classification = ClassificationUi.Waiting
        detections = emptyList()
        onChange()
    }

    /**
     * Klasifikasi satu frame kamera: frame dibagi menjadi petak, seluruh petak
     * diklasifikasi dalam satu batch, dan petak yang lolos ambang menjadi kotak
     * overlay (D-14). Dipanggil analyzer; hasil diabaikan bila pemantauan sudah
     * berhenti di tengah inferensi.
     */
    fun onFrame(frame: ArgbFrame) {
        if (!isRunning) return
        val profile = runningProfile() ?: return
        val tiles = FrameTiles.split(frame, columns = TILE_COLUMNS, rows = TILE_ROWS)
        if (tiles.isEmpty()) return
        val outcomes = classifier.classifyAll(profile, tiles.map { it.frame })
        if (!isRunning) return
        detections = TileDetectionFilter.select(tiles, outcomes, profile.confidenceThreshold)
        detectionsAtMillis = System.currentTimeMillis()
        classification = when (val top = outcomes.filterIsInstance<ClassifierOutcome.Classified>()
            .maxByOrNull { it.result.topProbability }) {
            null -> {
                val failure = outcomes.filterIsInstance<ClassifierOutcome.Failed>().firstOrNull()?.failure
                    ?: ClassifierFailure(
                        ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                        "model tidak mengeluarkan kelas",
                    )
                ClassificationUi.Failed(profile.name, failure)
            }

            else -> {
                val label = top.result.topLabel
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
                        probability = top.result.topProbability,
                        threshold = profile.confidenceThreshold,
                        runnerUp = top.result.ranked(4).drop(1),
                        tilesChecked = tiles.size,
                    )
                }
            }
        }
        onChange()
    }

    /** Keadaan yang harus berhenti saat profil aktif berubah atau hilang. */
    fun onActiveProfileChanged() {
        if (isRunning && runningProfile() == null) stop()
    }

    companion object {
        /** Petak 3x3: cukup kasar untuk posisi objek, cukup murah untuk CPU. */
        const val TILE_COLUMNS: Int = 3
        const val TILE_ROWS: Int = 3

        /** Kotak lebih tua dari ini ditandai usang oleh overlay (D-14). */
        const val DETECTION_STALE_MS: Long = 2_500L

        /** Pesan Mesin yang dipakai guard saat Mulai ditekan. */
        fun machineState(): MachineState = MachineState.Stopped

        /** Tahap 3 belum memiliki target aktif; nilai ini eksplisit, bukan tebakan. */
        fun hasActiveTarget(): Boolean = false
    }
}
