package id.pemilah.ui

import id.pemilah.camera.CameraStatus
import id.pemilah.inference.*
import id.pemilah.model.*
import id.pemilah.storage.SampleStore
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ModelTestSessionTest {
    private val profile=ModelProfile(1,"p","p","","p.onnx",listOf("Kaca","Plastik"),emptyMap(),InputSpec(2,2,Normalization.SCALE_0_1,TensorLayout.NCHW),OutputSpec(OutputFormat.CLASSIFICATION_ONLY,false,false),0.6,false,emptyMap())
    private val frame=ArgbFrame(IntArray(16){0xff00ff00.toInt()},4,4)
    private val classifier=object:Classifier {
        override fun classify(profile: ModelProfile,frame: ArgbFrame)=ClassifierOutcome.Classified(Classification(profile.labels,floatArrayOf(0.9f,0.1f)))
    }
    @Test fun capturesMultipleObjectsOnlyWhileTesting() {
        val root=Files.createTempDirectory("session").toFile()
        try {
            val store=SampleStore(root)
            val pipeline=ObjectPipeline(ObjectDetector {listOf(NormalizedRegion(0f,0f,0.5f,1f),NormalizedRegion(0.5f,0f,1f,1f))},classifier)
            val test=ModelTestSession(classifier,{ActiveProfileState.Selected(profile)},pipeline,store){byteArrayOf(1)}
            test.operation.updateCameraStatus(CameraStatus.Ready)
            test.operation.onFrame(frame);assertTrue(store.list().isEmpty())
            assertTrue(test.start());assertFalse(test.start())
            test.operation.onFrame(frame)
            assertEquals(2,store.list().single().objects.size)
            repeat(10) { test.operation.onFrame(frame) }
            assertEquals("Stationary objects must not produce repeated images", 1, store.list().size)
            assertEquals(1, test.saved)
            assertTrue(store.snapshot().references.isEmpty())
            test.stop();test.operation.onFrame(frame)
            assertEquals(1,store.list().size)
            assertTrue(test.start())
            test.operation.onFrame(frame)
            assertEquals(2, store.list().size)
            assertEquals(1, test.saved)
        } finally {root.deleteRecursively()}
    }
    @Test fun emptyDetectionDoesNotCreateSampleAndReportsEmptyState() {
        val root=Files.createTempDirectory("empty-session").toFile()
        try {
            val store=SampleStore(root)
            val test=ModelTestSession(classifier,{ActiveProfileState.Selected(profile)},ObjectPipeline(ObjectDetector {emptyList()},classifier),store){byteArrayOf(1)}
            test.operation.updateCameraStatus(CameraStatus.Ready);test.start();test.operation.onFrame(frame)
            assertEquals(ClassificationUi.NoObjects,test.operation.classification)
            assertTrue(store.list().isEmpty())
        } finally {root.deleteRecursively()}
    }
    @Test fun failedImageEncodingDoesNotPublishSample() {
        val root=Files.createTempDirectory("failed-session").toFile()
        try {
            val store=SampleStore(root)
            val test=ModelTestSession(classifier,{ActiveProfileState.Selected(profile)},ObjectPipeline(ObjectDetector {listOf(NormalizedRegion(0f,0f,1f,1f))},classifier),store){error("ENCODE_FAILED")}
            test.operation.updateCameraStatus(CameraStatus.Ready);test.start();test.operation.onFrame(frame)
            assertTrue(test.operation.classification is ClassificationUi.Failed)
            assertTrue(store.list().isEmpty());assertEquals(0,test.saved)
        } finally {root.deleteRecursively()}
    }
    @Test fun failedSaveIsRetriedWithoutEncodingStationaryDuplicates() {
        val root = Files.createTempDirectory("retry-session").toFile()
        try {
            val store = SampleStore(root)
            var attempts = 0
            val pipeline = ObjectPipeline(
                ObjectDetector { listOf(NormalizedRegion(0f, 0f, 1f, 1f)) }, classifier,
            )
            val test = ModelTestSession(
                classifier, { ActiveProfileState.Selected(profile) }, pipeline, store,
            ) {
                attempts++
                if (attempts == 1) error("ENCODE_FAILED")
                byteArrayOf(1)
            }
            test.operation.updateCameraStatus(CameraStatus.Ready)
            assertTrue(test.start())
            test.operation.onFrame(frame)
            assertEquals(0, test.saved)
            test.operation.onFrame(frame)
            repeat(10) { test.operation.onFrame(frame) }
            assertEquals(2, attempts)
            assertEquals(1, test.saved)
            assertEquals(1, store.list().size)
        } finally { root.deleteRecursively() }
    }
    @Test fun frameFromPreviousGenerationCannotPublishAfterRestart() {
        val entered=CountDownLatch(1);val release=CountDownLatch(1)
        val pipeline=ObjectPipeline(ObjectDetector {entered.countDown();check(release.await(5,TimeUnit.SECONDS));listOf(NormalizedRegion(0f,0f,1f,1f))},classifier)
        val session=OperationSession(classifier,{ActiveProfileState.Selected(profile)},objectPipeline=pipeline)
        session.updateCameraStatus(CameraStatus.Ready);session.start()
        val worker=Thread {session.onFrame(frame)};worker.start()
        assertTrue(entered.await(5,TimeUnit.SECONDS));session.stop();session.start();release.countDown();worker.join(5000)
        assertTrue(session.detections.isEmpty())
    }
    @Test fun monitoringLoadsCorrectionSnapshotOncePerStart() {
        var snapshot=CorrectionSnapshot()
        val pipeline=ObjectPipeline(ObjectDetector {listOf(NormalizedRegion(0f,0f,1f,1f))},classifier)
        val session=OperationSession(classifier,{ActiveProfileState.Selected(profile)},objectPipeline=pipeline,loadCorrections={snapshot})
        session.updateCameraStatus(CameraStatus.Ready);session.start()
        snapshot=CorrectionSnapshot(listOf(CorrectionReference(profile.modelFile,"Plastik",CorrectionMatcher.descriptor(frame))))
        session.onFrame(frame);assertEquals("Kaca",session.detections.single().label)
        session.stop();session.start();session.onFrame(frame)
        assertEquals("Plastik",session.detections.single().label)
        assertNotNull(session.detections.single().referenceSimilarity)
    }
}
