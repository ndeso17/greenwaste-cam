package id.pemilah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.PemilahColors
import id.pemilah.R

/**
 * Lebar rail navigasi. Nilai ini keputusan tata letak D-UI-02, bukan angka
 * mesin: 232 dp saat terbuka (ikon + label) dan 72 dp saat tertutup (hanya
 * ikon, tetap memenuhi target sentuh 48 dp ditambah padding 12 dp per sisi).
 */
internal object NavigationRailWidths {
    val expanded = 232.dp
    val collapsed = 72.dp
}

/**
 * Kerangka navigasi enam menu (UI-01–UI-06) sesuai `OperationScreen.xml`
 * `navigationRail`. Tujuan selain operasi menampilkan keadaan "belum
 * dikonfigurasi" secara jujur; tidak ada data mesin contoh (Design.md §6).
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    initialDestination: AppDestination = AppDestination.OPERATION,
) {
    var current by remember { mutableStateOf(initialDestination) }

    Row(modifier = modifier.fillMaxSize()) {
        NavigationRail(
            current = current,
            onSelect = { current = it },
        )
        DestinationContent(
            destination = current,
            onNavigate = { current = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun NavigationRail(
    current: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(NavigationRailWidths.expanded)
            .background(Palette.surfaceRaised)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.app_title),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                destination = destination,
                selected = destination == current,
                expanded = true,
                onClick = { onSelect(destination) },
            )
        }
    }
}

/**
 * Satu baris tujuan pada rail: ikon Material (D-05) + label tampilan (D-UI-02).
 * Tinggi minimum 48 dp dipenuhi lewat `minimumInteractiveComponentSize` dan
 * padding vertikal, sehingga target sentuh tetap sah pada kedua keadaan rail.
 */
@Composable
private fun NavigationRailItem(
    destination: AppDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (selected) Palette.onSurface else Palette.onSurfaceMuted
    val prdLabel = stringResource(destination.prdTitleRes)
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Palette.accent.copy(alpha = 0.18f) else Color.Transparent)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick,
            )
            // Deskripsi aksesibilitas memakai label PRD induk yang lengkap, bukan
            // label ringkas D-UI-02: pembaca layar mengucap "Bak & Robot", bukan "Bot".
            .semantics(mergeDescendants = true) {
                contentDescription = prdLabel
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = if (expanded) Arrangement.spacedBy(12.dp) else Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = destination.icon(),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        if (expanded) {
            Text(
                text = stringResource(destination.titleRes),
                color = tint,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    onNavigate: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination == AppDestination.OPERATION) {
        OperationScreen(
            modifier = modifier,
            onOpenModels = { onNavigate(AppDestination.MODELS) },
            onOpenHistory = { onNavigate(AppDestination.HISTORY) },
        )
        return
    }
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(destination.titleRes),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.state_not_configured),
            color = Palette.onSurfaceMuted,
            fontSize = 14.sp,
        )
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun AppNavigationPreview() {
    MaterialTheme(colorScheme = PemilahColors) {
        Surface {
            AppNavigation()
        }
    }
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun OperationScreenPreview() {
    MaterialTheme(colorScheme = PemilahColors) {
        Surface {
            OperationScreen(
                state = OperationUiState(
                    profileNameAndVersion = null,
                    calibration = CalibrationUiState.NONE,
                    session = null,
                ),
            )
        }
    }
}
