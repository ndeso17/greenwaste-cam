package id.pemilah.ui

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.PemilahColors

/** Controlled UI/callback evidence. This fixture is compiled only into debug APKs. */
class ShutterVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = intent.getStringExtra("mode") ?: "ready"
        setContent {
            MaterialTheme(colorScheme = PemilahColors) {
                if (mode == "contrast") {
                    ContrastFixture()
                } else {
                    var starts by remember { mutableStateOf(0) }
                    var stops by remember { mutableStateOf(0) }
                    var running by remember { mutableStateOf(mode == "active") }
                    val blocked = mode == "blocked"
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        OperationScreen(
                            state = OperationUiState(
                                hasCameraPermission = true,
                                profileNameAndVersion = if (blocked) null else "DEBUG FIXTURE",
                                calibration = if (blocked) CalibrationUiState.NONE else CalibrationUiState.VALID,
                                activeClassesConfigured = !blocked,
                                isRunning = running,
                            ),
                            onStart = { starts++; running = true },
                            onStop = { stops++; running = false },
                        )
                        Text(
                            "DEBUG FIXTURE $mode starts=$starts stops=$stops",
                            modifier = Modifier.align(Alignment.CenterStart)
                                .background(Color.Black).padding(4.dp),
                            color = Color.White, fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContrastFixture() {
    val whiteImage = remember {
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }.asImageBitmap()
    }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Box(Modifier.fillMaxSize().background(Color.White)
        .onGloballyPositioned { bounds = it.boundsInRoot() }) {
        CameraOverlaySurface(
            backdrop = whiteImage,
            cameraBounds = bounds,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp)
                .height(140.dp).semantics { contentDescription = "contrast panel" },
        ) {
            Text("CONTRAST", color = Color.White, fontSize = 24.sp,
                modifier = Modifier.align(Alignment.Center))
        }
    }
}
