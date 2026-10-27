package id.pemilah.onnx

import ai.onnxruntime.NodeInfo
import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import id.pemilah.model.OnnxInspectionCode
import id.pemilah.model.OnnxInspectionProblem
import id.pemilah.model.OnnxInspectionResult
import id.pemilah.model.OnnxMetadata
import id.pemilah.model.OnnxModelInfo
import id.pemilah.model.OnnxModelInspector

/**
 * Inspeksi berkas model ONNX memakai ONNX Runtime (D-09, MOD-01, MOD-02).
 *
 * Aturan yang dijaga:
 * - Seluruh informasi berasal dari berkas model: metadata `names` untuk label,
 *   `inputNames`/`outputNames` untuk bentuk tensor. Tidak ada nilai model yang
 *   dikarang aplikasi.
 * - Sesi ONNX selalu ditutup, baik pada jalur berhasil maupun gagal, supaya
 *   berkas yang ditolak tidak menahan memori.
 * - Setiap kegagalan dikembalikan sebagai [OnnxInspectionResult.Rejected] dengan
 *   kode stabil; tidak ada `OrtException` yang lolos ke lapisan UI
 *   (`Styleguide.md` §3).
 *
 * Kelas ini tidak diuji unit karena membutuhkan pustaka native; aturan murni
 * pembacaan metadata diuji pada `OnnxMetadataTest`.
 */
class OrtOnnxModelInspector : OnnxModelInspector {

    override fun inspect(bytes: ByteArray): OnnxInspectionResult {
        if (bytes.isEmpty()) {
            return reject(OnnxInspectionCode.EMPTY_FILE)
        }
        val environment = OrtEnvironment.getEnvironment()
        val session = try {
            environment.createSession(bytes, OrtSession.SessionOptions())
        } catch (error: OrtException) {
            return reject(OnnxInspectionCode.UNREADABLE_MODEL, detailOf(error))
        }
        return session.use { open ->
            val labels = OnnxMetadata.parseClassNames(open.labelsMetadata())
                ?: return@use reject(OnnxInspectionCode.MISSING_CLASS_NAMES)
            val inputShape = open.inputShape()
                ?: return@use reject(OnnxInspectionCode.UNSUPPORTED_INPUT_SHAPE)
            val inputSpec = OnnxMetadata.inputSpecOf(inputShape)
                ?: return@use reject(
                    OnnxInspectionCode.UNSUPPORTED_INPUT_SHAPE,
                    detail = inputShape.joinToString(prefix = "[", postfix = "]"),
                )
            val outputShape = open.outputShape()
                ?: return@use reject(
                    OnnxInspectionCode.UNSUPPORTED_OUTPUT_SHAPE,
                    detail = "jumlah keluaran bukan satu",
                )
            val outputSpec = OnnxMetadata.outputSpecOf(outputShape, labels.size)
                ?: return@use reject(
                    OnnxInspectionCode.UNSUPPORTED_OUTPUT_SHAPE,
                    detail = outputShape.joinToString(prefix = "[", postfix = "]") +
                        " untuk ${labels.size} kelas",
                )
            OnnxInspectionResult.Inspected(
                OnnxModelInfo(
                    labels = labels,
                    input = inputSpec,
                    output = outputSpec,
                    modelVersion = open.versionMetadata(),
                ),
            )
        }
    }

    /** Metadata `names`; `null` bila model tidak menuliskan kelasnya. */
    private fun OrtSession.labelsMetadata(): String? =
        runCatching { metadata.customMetadata["names"] }.getOrNull()

    private fun OrtSession.versionMetadata(): String? =
        runCatching { metadata.customMetadata["version"] }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Bentuk tensor masukan pertama. Hanya keluaran gambar tunggal yang
     * didukung; model dengan beberapa masukan ditolak karena kontrak inferensi
     * kamera belum menetapkan masukan tambahan (B-02).
     */
    private fun OrtSession.inputShape(): List<Long>? {
        val infos = runCatching { inputInfo }.getOrNull() ?: return null
        if (infos.size != 1) return null
        return tensorShape(infos.values.single())
    }

    /** Bentuk tensor keluaran pertama; model multi-keluaran ditolak. */
    private fun OrtSession.outputShape(): List<Long>? {
        val infos = runCatching { outputInfo }.getOrNull() ?: return null
        if (infos.size != 1) return null
        return tensorShape(infos.values.single())
    }

    private fun tensorShape(info: NodeInfo): List<Long>? {
        val tensor = info.info as? TensorInfo ?: return null
        if (tensor.type != OnnxJavaType.FLOAT) return null
        return tensor.shape?.toList()
    }
}

private fun reject(code: OnnxInspectionCode, detail: String? = null) =
    OnnxInspectionResult.Rejected(OnnxInspectionProblem(code, detail))

private fun detailOf(error: OrtException): String =
    error.message?.take(MAX_DETAIL_CHARS) ?: error::class.java.simpleName

private const val MAX_DETAIL_CHARS = 200
