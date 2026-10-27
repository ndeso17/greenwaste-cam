package id.pemilah

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import id.pemilah.ui.AppNavigation

/** Palet acuan D-UI-01 (.work/Design.md bagian 2). Token, bukan identitas merek final. */
internal object Palette {
    val surface = Color(0xFF0F1115)
    val surfaceRaised = Color(0xFF171A21)
    val onSurface = Color(0xFFE6E8EC)
    val onSurfaceMuted = Color(0xFF9AA0AB)
    val accent = Color(0xFF2F6FED)
    val success = Color(0xFF2E9E5B)
    val warning = Color(0xFFC98A12)
    val danger = Color(0xFFD64545)
    val neutralClass = Color(0xFF7C8595)
}

internal val PemilahColors = darkColorScheme(
    background = Palette.surface,
    surface = Palette.surface,
    onBackground = Palette.onSurface,
    onSurface = Palette.onSurface,
    primary = Palette.accent,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Follow the physical device, including when system auto-rotation is locked.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        setContent {
            MaterialTheme(colorScheme = PemilahColors) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.surface),
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
