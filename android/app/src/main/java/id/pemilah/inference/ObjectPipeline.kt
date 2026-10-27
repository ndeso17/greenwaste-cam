package id.pemilah.inference

import id.pemilah.model.ModelProfile

fun interface ObjectDetector { fun detect(frame: ArgbFrame): List<NormalizedRegion> }
data class AnalyzedObject(
    val region: NormalizedRegion,
    val originalLabel: String,
    val probability: Float,
    val descriptor: List<Float>,
    val correction: CorrectionMatch? = null,
) { val label: String get() = correction?.label ?: originalLabel }

class ObjectPipeline(private val detector: ObjectDetector, private val classifier: Classifier,
    val modelId: (ModelProfile) -> String = { it.modelFile },
) : AutoCloseable {
    private var closed=false
    @Synchronized override fun close() {
        closed=true
        (detector as? AutoCloseable)?.close()
        (classifier as? AutoCloseable)?.close()
    }
    @Synchronized fun analyze(profile: ModelProfile, frame: ArgbFrame, references: CorrectionSnapshot = CorrectionSnapshot()): List<AnalyzedObject> {
        check(!closed) { "PIPELINE_CLOSED" }
        val boxes=detector.detect(frame)
        val crops=boxes.map { ObjectDecoder.crop(frame,it) }
        val outcomes=classifier.classifyAll(profile,crops)
        return boxes.indices.mapNotNull { i ->
            val result=(outcomes.getOrNull(i) as? ClassifierOutcome.Classified)?.result
                ?: throw IllegalStateException((outcomes.getOrNull(i) as? ClassifierOutcome.Failed)?.failure?.code?.name ?: "CLASSIFICATION_FAILED")
            val label=result.topLabel ?: return@mapNotNull null
            val descriptor=CorrectionMatcher.descriptor(crops[i])
            AnalyzedObject(boxes[i],label,result.topProbability,descriptor,
                CorrectionMatcher.match(modelId(profile),descriptor,references)?.takeIf { it.label in profile.labels })
        }
    }
}
