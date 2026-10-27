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
        val prepared = prepare(profile)
        if (prepared == null) {
            return ClassifierOutcome.Failed(
                lastFailure ?: ClassifierFailure(
                    ClassifierFailureCode.MODEL_FILE_MISSING,
                    profile.modelFile,
                ),
            )
        }
        return infer(prepared, listOf(frame)).firstOrNull()
            ?: failed(ClassifierFailureCode.RUN_FAILED, "tidak ada keluaran")
    }

    /**
     * Klasifikasi batch: seluruh petak dibungkus satu tensor `[N, 3, H, W]`.
     *
     * Bila model tidak menerima batch (mis. batch tetap 1), eksekusi batch gagal
     * dan setiap petak dijalankan satu per satu. Hasil akhirnya sama; yang
     * berbeda hanya jumlah eksekusi sesi.
     */
    override fun classifyAll(
        profile: ModelProfile,
        frames: List<ArgbFrame>,
    ): List<ClassifierOutcome> {
        if (frames.isEmpty()) return emptyList()
        val prepared = prepare(profile) ?: return frames.map {
            lastFailure ?: ClassifierFailure(ClassifierFailureCode.MODEL_FILE_MISSING)
        }.map { ClassifierOutcome.Failed(it) }
        if (frames.size == 1) return infer(prepared, frames)
        val batched = infer(prepared, frames)
        if (batched.size == frames.size && batched.none { it is ClassifierOutcome.Failed }) {
            return batched
        }
        return frames.map { frame -> classify(profile, frame) }
    }

    private class Prepared(val session: OrtSession, val profile: ModelProfile)

    /** Kegagalan terakhir pada `prepare`; dipakai `classifyAll` untuk batch kosong. */
    private var lastFailure: ClassifierFailure? = null

    private fun prepare(profile: ModelProfile): Prepared? {
        lastFailure = null
        val bytes = store.readModelFile(profile.modelFile)
        if (bytes == null) {
            lastFailure = ClassifierFailure(ClassifierFailureCode.MODEL_FILE_MISSING, profile.modelFile)
            return null
        }
        val open = try {
            openSession(profile.modelFile, bytes)
        } catch (error: OrtException) {
            lastFailure = ClassifierFailure(
                ClassifierFailureCode.MODEL_UNREADABLE,
                detailOf(error),
            )
            return null
        }
        return Prepared(open, profile)
    }

    /**
     * Satu eksekusi sesi untuk seluruh [frames].
     *
     * Bentuk keluaran model harus `[N, C]`; `[1, C]` untuk `N` petak tidak dapat
     * dipetakan per petak sehingga dilaporkan gagal agar pemanggil dapat mundur
     * ke jalur satu-per-satu.
     */
    private fun infer(prepared: Prepared, frames: List<ArgbFrame>): List<ClassifierOutcome> {
        val profile = prepared.profile
        val tensor = try {
            concatTensor(frames, profile)
        } catch (error: IllegalArgumentException) {
            return frames.map {
                ClassifierOutcome.Failed(
                    ClassifierFailure(ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH, error.message),
                )
            }
        }
        val shape = longArrayOf(
            frames.size.toLong(),
            3,
            profile.input.height.toLong(),
            profile.input.width.toLong(),
        )
        return try {
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(tensor), shape).use { input ->
                val inputName = prepared.session.inputNames.firstOrNull()
                    ?: return frames.map {
                        ClassifierOutcome.Failed(
                            ClassifierFailure(
                                ClassifierFailureCode.MODEL_UNREADABLE,
                                "tidak ada masukan",
                            ),
                        )
                    }
                prepared.session.run(mapOf(inputName to input)).use { result ->
                    val rows = readLogits(result.get(0))
                    if (rows == null || rows.size != frames.size) {
                        return frames.map {
                            ClassifierOutcome.Failed(
                                ClassifierFailure(
                                    ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                                    "keluaran bukan [${
                                        frames.size
                                    }, ${profile.labels.size}]",
                                ),
                            )
                        }
                    }
                    rows.map { logits ->
                        if (logits.size != profile.labels.size) {
                            ClassifierOutcome.Failed(
                                ClassifierFailure(
                                    ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH,
                                    "keluaran ${logits.size} nilai, label ${profile.labels.size}",
                                ),
                            )
                        } else {
                            ClassifierOutcome.Classified(
                                Classification.fromLogits(profile.labels, logits),
                            )
                        }
                    }
                }
            }
        } catch (error: OrtException) {
            frames.map {
                ClassifierOutcome.Failed(ClassifierFailure(ClassifierFailureCode.RUN_FAILED, detailOf(error)))
            }
        }
    }

    private fun concatTensor(frames: List<ArgbFrame>, profile: ModelProfile): FloatArray {
        val perFrame = 3 * profile.input.width * profile.input.height
        val tensor = FloatArray(perFrame * frames.size)
        frames.forEachIndexed { index, frame ->
            val single = FramePreprocess.toTensor(frame, profile.input)
            require(single.size == perFrame) {
                "tensor petak ${single.size} tidak sama dengan $perFrame"
            }
            System.arraycopy(single, 0, tensor, index * perFrame, perFrame)
        }
        return tensor
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

    private fun readLogits(value: Any?): List<FloatArray>? {
        val tensor = value as? OnnxTensor ?: return null
        val info = tensor.info as? TensorInfo ?: return null
        val shape = info.shape
        // Keluaran klasifikasi: [N, C]. Bentuk lain ditolak.
        if (shape.size != 2) return null
        val rows = (tensor.value as? Array<*>)?.mapNotNull { it as? FloatArray } ?: return null
        return rows.takeIf { it.size.toLong() == shape[0] }
    }

    private fun failed(code: ClassifierFailureCode, detail: String?): ClassifierOutcome =
        ClassifierOutcome.Failed(ClassifierFailure(code, detail))

    private fun detailOf(error: OrtException): String =
        error.message?.lineSequence()?.firstOrNull() ?: error::class.java.simpleName

    private companion object {
        val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    }
}
