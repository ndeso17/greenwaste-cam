package id.pemilah.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import id.pemilah.inference.ArgbFrame
import id.pemilah.inference.Classification
import id.pemilah.inference.Classifier
import id.pemilah.inference.ClassifierFailure
import id.pemilah.inference.ClassifierFailureCode
import id.pemilah.inference.ClassifierOutcome
import id.pemilah.inference.FramePreprocess
import id.pemilah.model.ModelProfile
import id.pemilah.storage.ConfigStore
import java.nio.FloatBuffer

/**
 * Klasifikasi frame memakai ONNX Runtime (D-12, D-13).
 *
 * Aturan yang dijaga:
 * - Berkas model dibaca dari penyimpanan aplikasi lewat [ConfigStore]; model yang
 *   hilang dilaporkan sebagai [ClassifierFailureCode.MODEL_FILE_MISSING], bukan
 *   diganti model lain.
 * - Sesi dibangun sekali per berkas model dan dipakai ulang; berkas model yang
 *   berbeda memaksa sesi baru sehingga model aktif tidak tertukar.
 * - Keluaran harus berbentuk `[1, C]` dengan `C` sama dengan jumlah label profil.
 *   Bentuk lain dilaporkan sebagai [ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH],
 *   bukan dipotong diam-diam.
 * - Seluruh `OrtException` ditangkap dan diterjemahkan ke kode stabil; lapisan UI
 *   tidak pernah menerima exception.
 *
 * Kelas ini tidak diuji unit karena membutuhkan pustaka native. Praproses dan
 * softmax diuji terpisah pada `FramePreprocessTest`.
 */
class OrtClassifier(private val store: ConfigStore) : Classifier {

    private var session: OrtSession? = null
    private var sessionModelFile: String? = null

    override fun classify(profile: ModelProfile, frame: ArgbFrame): ClassifierOutcome {
        val modelFile = profile.modelFile
        val bytes = store.readModelFile(modelFile)
            ?: return failed(ClassifierFailureCode.MODEL_FILE_MISSING, modelFile)
        val open = try {
            openSession(modelFile, bytes)
        } catch (error: OrtException) {
            return failed(ClassifierFailureCode.MODEL_UNREADABLE, detailOf(error))
        }
        val tensor = try {
            FramePreprocess.toTensor(frame, profile.input)
        } catch (error: IllegalArgumentException) {
            return failed(ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH, error.message)
        }
        val shape = longArrayOf(1, 3, profile.input.height.toLong(), profile.input.width.toLong())
        return try {
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensor), shape).use { input ->
                val inputName = open.inputNames.firstOrNull()
                    ?: return failed(ClassifierFailureCode.MODEL_UNREADABLE, "tidak ada masukan")
                open.run(mapOf(inputName to input)).use { result ->
                    val logits = readLogits(result.get(0)) ?: return failed(
                        ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                        "keluaran bukan tensor [1, ${profile.labels.size}]",
                    )
                    if (logits.size != profile.labels.size) {
                        return failed(
                            ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                            "keluaran ${logits.size} nilai, label ${profile.labels.size}",
                        )
                    }
                    ClassifierOutcome.Classified(Classification.fromLogits(profile.labels, logits))
                }
            }
        } catch (error: OrtException) {
            failed(ClassifierFailureCode.RUN_FAILED, detailOf(error))
        }
    }

    /** Tutup sesi aktif; dipanggil saat pemantauan berhenti atau model berganti. */
    fun close() {
        runCatching { session?.close() }
        session = null
        sessionModelFile = null
    }

    private fun openSession(modelFile: String, bytes: ByteArray): OrtSession {
        session?.takeIf { sessionModelFile == modelFile }?.let { return it }
        close()
        return environment.createSession(bytes, OrtSession.SessionOptions()).also {
            session = it
            sessionModelFile = modelFile
        }
    }

    private fun readLogits(value: Any?): FloatArray? {
        val tensor = value as? OnnxTensor ?: return null
        val info = tensor.info as? TensorInfo ?: return null
        val shape = info.shape
        // Keluaran klasifikasi: [1, C] (batch satu). Bentuk lain ditolak.
        if (shape.size != 2 || shape[0] != 1L) return null
        return when (val raw = tensor.value) {
            is Array<*> -> (raw.firstOrNull() as? FloatArray)
            else -> null
        }
    }

    private fun failed(code: ClassifierFailureCode, detail: String?): ClassifierOutcome =
        ClassifierOutcome.Failed(ClassifierFailure(code, detail))

    private fun detailOf(error: OrtException): String =
        error.message?.lineSequence()?.firstOrNull() ?: error::class.java.simpleName

    private companion object {
        val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    }
}
