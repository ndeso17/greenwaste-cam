package id.pemilah.ui

import id.pemilah.camera.CameraStatus
import id.pemilah.domain.waste.WasteClass
import id.pemilah.inference.ArgbFrame
import id.pemilah.inference.Classification
import id.pemilah.inference.Classifier
import id.pemilah.inference.ClassifierFailure
import id.pemilah.inference.ClassifierFailureCode
import id.pemilah.inference.ClassifierOutcome
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ActiveProfileState
import id.pemilah.model.InputSpec
import id.pemilah.model.ModelProfile
import id.pemilah.model.Normalization
import id.pemilah.model.OutputFormat
import id.pemilah.model.OutputSpec
import id.pemilah.model.TensorLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti pemilik state layar Operasi (D-11, D-13): Mulai hanya lolos dengan
 * kamera siap + model aktif, frame diabaikan saat pemantauan berhenti, hasil
 * klasifikasi membawa ambang profil, dan kegagalan model dilaporkan apa adanya.
 */
class OperationSessionTest {

    private val labels = listOf("Organik", "Plastik", "Kaleng", "Styrofoam", "Kaca", "Kardus", "Besi")

    private val profile = ModelProfile(
        schemaVersion = 1,
        profileId = "resnet18-7kelas",
        name = "resnet18-7kelas",
        version = "7kelas",
        modelFile = "model-resnet18-7kelas.onnx",
        labels = labels,
        classMapping = labels.associateWith { WasteClass.entries[labels.indexOf(it)] },
        input = InputSpec(224, 224, Normalization.SCALE_0_1, TensorLayout.NCHW),
        output = OutputSpec(OutputFormat.CLASSIFICATION_ONLY, boxesNormalized = false, hasObjectness = false),
        confidenceThreshold = 0.6,
        groupingEnabled = false,
        binAssignment = emptyMap(),
    )

    private val frame = ArgbFrame(IntArray(4) { 0xFF808080.toInt() }, width = 2, height = 2)

    private class FakeClassifier(
        private val outcome: ClassifierOutcome,
        private val onClassify: () -> Unit = {},
    ) : Classifier {
        var calls = 0
        override fun classify(profile: ModelProfile, frame: ArgbFrame): ClassifierOutcome {
            calls++
            onClassify()
            return outcome
        }
    }

    private fun classified(vararg pairs: Pair<String, Float>) = ClassifierOutcome.Classified(
        Classification(labels = labels, probabilities = probabilities(pairs)),
    )

    private fun probabilities(pairs: Array<out Pair<String, Float>>): FloatArray {
        val values = FloatArray(labels.size) { 0.01f }
        pairs.forEach { (label, probability) -> values[labels.indexOf(label)] = probability }
        return values
    }

    private fun session(
        classifier: Classifier,
        active: ActiveProfileState = ActiveProfileState.Selected(profile),
    ) = OperationSession(classifier = classifier, activeProfile = { active })

    @Test
    fun start_withCameraReadyAndActiveModel_runs() {
        val session = session(FakeClassifier(classified("Kaca" to 0.9f)))
        session.updateCameraStatus(CameraStatus.Ready)

        assertTrue(session.start())
        assertTrue(session.isRunning)
        assertEquals(ClassificationUi.Waiting, session.classification)
    }

    @Test
    fun start_withoutCameraReady_isRefused() {
        val session = session(FakeClassifier(classified("Kaca" to 0.9f)))

        assertFalse(session.start())
        assertFalse(session.isRunning)
    }

    @Test
    fun start_withoutActiveModel_isRefused() {
        val session = session(
            FakeClassifier(classified("Kaca" to 0.9f)),
            active = ActiveProfileState.None(ActiveProfileAbsentReason.NOT_SELECTED),
        )
        session.updateCameraStatus(CameraStatus.Ready)

        assertFalse(session.start())
        assertFalse(session.isRunning)
    }

    @Test
    fun frame_whileStopped_isNotClassified() {
        val classifier = FakeClassifier(classified("Kaca" to 0.9f))
        val session = session(classifier)
        session.updateCameraStatus(CameraStatus.Ready)

        session.onFrame(frame)

        assertEquals(0, classifier.calls)
        assertEquals(ClassificationUi.Waiting, session.classification)
    }

    @Test
    fun frame_whileRunning_reportsTopLabelAndThreshold() {
        val session = session(FakeClassifier(classified("Kaca" to 0.9f, "Kardus" to 0.05f)))
        session.updateCameraStatus(CameraStatus.Ready)
        session.start()

        session.onFrame(frame)

        val result = session.classification as ClassificationUi.Result
        assertEquals("Kaca", result.label)
        assertEquals(0.9f, result.probability, 0.0001f)
        assertEquals(0.6, result.threshold, 0.0001)
        assertFalse(result.belowThreshold)
        assertEquals("Kardus", result.runnerUp.first().first)
        assertEquals("resnet18-7kelas", result.profileName)
    }

    @Test
    fun frame_belowThreshold_isMarked() {
        val session = session(FakeClassifier(classified("Organik" to 0.35f)))
        session.updateCameraStatus(CameraStatus.Ready)
        session.start()

        session.onFrame(frame)

        val result = session.classification as ClassificationUi.Result
        assertTrue("0.35 di bawah ambang 0.6", result.belowThreshold)
    }

    @Test
    fun frame_classifierFailure_isReportedWithCode() {
        val failure = ClassifierFailure(ClassifierFailureCode.MODEL_FILE_MISSING, "model-x.onnx")
        val session = session(FakeClassifier(ClassifierOutcome.Failed(failure)))
        session.updateCameraStatus(CameraStatus.Ready)
        session.start()

        session.onFrame(frame)

        val failed = session.classification as ClassificationUi.Failed
        assertEquals(failure, failed.failure)
        assertEquals("resnet18-7kelas", failed.profileName)
    }

    @Test
    fun stop_clearsResultAndStopsClassification() {
        val classifier = FakeClassifier(classified("Kaca" to 0.9f))
        val session = session(classifier)
        session.updateCameraStatus(CameraStatus.Ready)
        session.start()
        session.onFrame(frame)

        session.stop()
        session.onFrame(frame)

        assertFalse(session.isRunning)
        assertEquals(ClassificationUi.Waiting, session.classification)
        assertEquals("frame sesudah berhenti tidak diklasifikasi", 1, classifier.calls)
    }

    @Test
    fun activeProfileRemoved_stopsRunningSession() {
        var active: ActiveProfileState = ActiveProfileState.Selected(profile)
        val session = OperationSession(
            classifier = FakeClassifier(classified("Kaca" to 0.9f)),
            activeProfile = { active },
        )
        session.updateCameraStatus(CameraStatus.Ready)
        session.start()

        active = ActiveProfileState.None(ActiveProfileAbsentReason.FILE_MISSING, profile.profileId)
        session.onActiveProfileChanged()

        assertFalse(session.isRunning)
    }

    @Test
    fun onChange_firesOnStartStopResultAndCameraStatus() {
        val session = session(FakeClassifier(classified("Kaca" to 0.9f)))
        var changes = 0
        session.onChange = { changes++ }

        session.updateCameraStatus(CameraStatus.Ready)
        session.updateCameraStatus(CameraStatus.Ready) // status sama: tidak ada notifikasi
        session.start()
        session.onFrame(frame)
        session.stop()

        assertEquals(4, changes)
    }
}
