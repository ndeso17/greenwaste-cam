package id.pemilah.inference

import id.pemilah.model.*
import org.junit.Assert.*
import org.junit.Test

class ObjectPipelineTest {
    @Test fun twoCropsAreClassifiedIndependently() {
        val crops=mutableListOf<Int>()
        val classifier=object: Classifier {
            override fun classify(profile: ModelProfile,frame: ArgbFrame): ClassifierOutcome {
                crops+=frame.pixels.first()
                return ClassifierOutcome.Classified(Classification(listOf("Plastik","Kaca"),if(frame.pixels.first()==1) floatArrayOf(0.9f,0.1f) else floatArrayOf(0.1f,0.9f)))
            }
        }
        val profile=ModelProfile(schemaVersion=1,profileId="p",name="p",version="",modelFile="p.onnx",labels=listOf("Plastik","Kaca"),classMapping=emptyMap(),input=InputSpec(2,2,Normalization.SCALE_0_1,TensorLayout.NCHW),output=OutputSpec(OutputFormat.CLASSIFICATION_ONLY,false,false),confidenceThreshold=0.6,groupingEnabled=false,binAssignment=emptyMap())
        val pipeline=ObjectPipeline(ObjectDetector { listOf(NormalizedRegion(0f,0f,0.5f,1f),NormalizedRegion(0.5f,0f,1f,1f)) },classifier)
        val objects=pipeline.analyze(profile,ArgbFrame(intArrayOf(1,2,1,2),2,2))
        assertEquals(listOf(1,2),crops)
        assertEquals(listOf("Plastik","Kaca"),objects.map { it.label })
    }
}
