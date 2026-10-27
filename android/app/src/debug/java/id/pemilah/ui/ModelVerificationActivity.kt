package id.pemilah.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import id.pemilah.PemilahColors
import id.pemilah.camera.CameraStatus
import id.pemilah.inference.*
import id.pemilah.model.*
import id.pemilah.onnx.*
import id.pemilah.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Debug-only native runtime verification, isolated from the operator's gallery. */
class ModelVerificationActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var report by remember { mutableStateOf("…") }
            LaunchedEffect(Unit) { report=withContext(Dispatchers.IO) { verify() } }
            MaterialTheme(colorScheme=PemilahColors) { Surface { Text(report) } }
        }
    }
    private fun verify(): String {
        val reportFile=File(filesDir,"verification-report.txt")
        val classifier=OrtClassifier(ConfigStore(FileConfigStorage(applicationContext)))
        val detector=OrtObjectDetector(applicationContext)
        return try {
            val profile=(ProfileRepository(ConfigStore(FileConfigStorage(applicationContext))).active() as ActiveProfileState.Selected).profile
            val bitmap=BitmapFactory.decodeFile(File(filesDir,"verification-input.jpg").path) ?: error("INPUT_MISSING")
            val pixels=IntArray(bitmap.width*bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            val frame=ArgbFrame(pixels,bitmap.width,bitmap.height)
            val store=SampleStore(File(filesDir,"verification-samples"))
            store.list().forEach { store.delete(it.id) }
            val pipeline=ObjectPipeline(detector,classifier)
            val test=ModelTestSession(classifier,{ActiveProfileState.Selected(profile)},pipeline,store){ f ->
                val image=Bitmap.createBitmap(f.pixels,f.width,f.height,Bitmap.Config.ARGB_8888)
                try { ByteArrayOutputStream().use { out -> check(image.compress(Bitmap.CompressFormat.JPEG,90,out));out.toByteArray() } }
                finally { image.recycle() }
            }
            test.operation.updateCameraStatus(CameraStatus.Ready)
            check(test.start())
            test.operation.onFrame(frame)
            val failure=test.operation.classification as? ClassificationUi.Failed
            check(failure==null) { failure.toString() }
            test.stop()
            val sample=store.list().single()
            check(sample.objects.size>=2) { "EXPECTED_MULTIPLE_OBJECTS: ${sample.objects.size}" }
            val changed=profile.labels.first {it!=sample.objects.first().predictedLabel}
            store.correct(sample.id,sample.objects.first().id,changed,profile.labels.toSet())
            val reloaded=SampleStore(File(filesDir,"verification-samples"))
            check(reloaded.list().single().objects.drop(1).all { it.correctedLabel==null })
            val monitoring=OperationSession(classifier,{ActiveProfileState.Selected(profile)},objectPipeline=pipeline,loadCorrections={reloaded.snapshot()})
            monitoring.updateCameraStatus(CameraStatus.Ready);check(monitoring.start());monitoring.onFrame(frame)
            check(monitoring.detections.any {it.label==changed && it.referenceSimilarity!=null})
            monitoring.stop()
            val countBefore=reloaded.list().size
            test.operation.onFrame(frame)
            check(reloaded.list().size==countBefore)
            val result="PASS native YOLO + ResNet; objects=${sample.objects.size}; JPEG persisted; correction per object; reopened store; reference applied next start; stopped capture unchanged"
            reportFile.writeText(result)
            bitmap.recycle()
            result
        } catch(e: Exception) {
            ("FAIL ${e.javaClass.simpleName}: ${e.message}").also {reportFile.writeText(it)}
        } finally { detector.close();classifier.close() }
    }
}
