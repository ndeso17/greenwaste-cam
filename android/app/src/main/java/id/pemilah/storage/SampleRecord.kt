package id.pemilah.storage

import kotlinx.serialization.Serializable

@Serializable
data class SampleObject(
    val id: String,
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val predictedLabel: String, val probability: Float,
    val descriptor: List<Float>,
    val correctedLabel: String? = null,
    val correctedAt: Long? = null,
)
@Serializable
data class SampleRecord(
    val schemaVersion: Int = 1,
    val id: String,
    val sessionId: String,
    val modelId: String,
    val modelName: String,
    val labels: List<String> = emptyList(),
    val detectorId: String = "yolov10n-coco-a77dd863933f",
    val capturedAt: Long,
    val width: Int, val height: Int,
    val objects: List<SampleObject>,
)
