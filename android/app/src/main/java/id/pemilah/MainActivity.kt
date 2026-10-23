package id.pemilah

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Palet acuan D-UI-01 (.work/Design.md bagian 2). Token, bukan identitas merek final. */
private object Palette {
    val surface = Color(0xFF0F1115)
    val onSurface = Color(0xFFE6E8EC)
    val onSurfaceMuted = Color(0xFF9AA0AB)
    val accent = Color(0xFF2F6FED)
}

private val PemilahColors = darkColorScheme(
    background = Palette.surface,
    surface = Palette.surface,
    onBackground = Palette.onSurface,
    onSurface = Palette.onSurface,
    primary = Palette.accent,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = PemilahColors) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Palette.surface),
                ) {
                    SkeletonScreen()
                }
            }
        }
    }
}

/**
 * Kerangka aplikasi: menyatakan dengan jujur bahwa belum ada profil, kalibrasi,
 * maupun koneksi controller. Tidak menampilkan data mesin palsu (Design.md §4.4).
 */
@Composable
fun SkeletonScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.app_title),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.skeleton_state),
            color = Palette.onSurfaceMuted,
            fontSize = 14.sp,
        )
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun SkeletonPreview() {
    MaterialTheme(colorScheme = PemilahColors) {
        SkeletonScreen()
    }
}
