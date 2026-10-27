package id.pemilah.onnx

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import id.pemilah.inference.*
import java.nio.FloatBuffer

/** The bundled model supplies locations only; its historical class labels are not product classes. */
class OrtObjectDetector(context: Context) : ObjectDetector, AutoCloseable {
    private val assets=context.applicationContext.assets
    private var session: OrtSession?=null
    @Synchronized override fun detect(frame: ArgbFrame): List<NormalizedRegion> {
        val environment=OrtEnvironment.getEnvironment()
        val model=session ?: assets.open("object-detector.onnx").use { stream ->
            OrtSession.SessionOptions().use { options ->
                environment.createSession(stream.readBytes(),options).also { session=it }
            }
        }
        val input=model.inputInfo.values.firstOrNull()?.info as? TensorInfo
        require(input?.shape?.contentEquals(longArrayOf(1,3,640,640))==true) { "DETECTOR_INPUT_UNSUPPORTED" }
        OnnxTensor.createTensor(environment,FloatBuffer.wrap(ObjectDecoder.letterbox(frame)),longArrayOf(1,3,640,640)).use { tensor ->
            model.run(mapOf(model.inputNames.first() to tensor)).use { result ->
                val output=result[0] as? OnnxTensor ?: error("DETECTOR_OUTPUT_UNSUPPORTED")
                require(output.info.shape.contentEquals(longArrayOf(1,300,6))) { "DETECTOR_OUTPUT_UNSUPPORTED" }
                val batch=output.value as? Array<*> ?: error("DETECTOR_OUTPUT_UNSUPPORTED")
                val channels=batch.firstOrNull() as? Array<*> ?: error("DETECTOR_OUTPUT_UNSUPPORTED")
                val rows=channels.map { it as? FloatArray ?: error("DETECTOR_OUTPUT_UNSUPPORTED") }.toTypedArray()
                return ObjectDecoder.decodeEndToEnd(rows,frame.width,frame.height)
            }
        }
    }
    @Synchronized override fun close() { session?.close(); session=null }
}
