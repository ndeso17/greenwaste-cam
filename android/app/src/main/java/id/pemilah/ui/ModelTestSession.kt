package id.pemilah.ui

import id.pemilah.inference.*
import id.pemilah.model.*
import id.pemilah.storage.*
import java.util.UUID

/** Shares the operation projection, but exclusively owns test sample capture. */
class ModelTestSession(
    classifier: Classifier,
    activeProfile: () -> ActiveProfileState,
    pipeline: ObjectPipeline,
    private val samples: SampleStore,
    private val encodeImage: (ArgbFrame) -> ByteArray,
) {
    private var sessionId=UUID.randomUUID().toString()
    var saved=0
        private set
    val operation=OperationSession(classifier,activeProfile,objectPipeline=pipeline,showLowConfidence=true,onAnalyzed={ frame,objects,profile,at ->
        if(objects.isNotEmpty()) {
            val id=UUID.randomUUID().toString()
            val record=SampleRecord(id=id,sessionId=sessionId,modelId=pipeline.modelId(profile),modelName=profile.name,labels=profile.labels,
                capturedAt=at,width=frame.width,height=frame.height,objects=objects.mapIndexed { index,o ->
                    SampleObject(index.toString(),o.region.left,o.region.top,o.region.right,o.region.bottom,
                        o.originalLabel,o.probability,o.descriptor)
                })
            samples.save(record,encodeImage(frame))
            saved++
        }
    })
    fun start(): Boolean {
        if(operation.isRunning) return false
        sessionId=UUID.randomUUID().toString()
        saved=0
        return operation.start()
    }
    fun stop()=operation.stop()
}
