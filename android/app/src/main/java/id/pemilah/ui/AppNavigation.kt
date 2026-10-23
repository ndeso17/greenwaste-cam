package id.pemilah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.PemilahColors
import id.pemilah.R

/**
 * Kerangka navigasi enam menu (UI-01–UI-06). Setiap tujuan menampilkan keadaan
 * "belum dikonfigurasi" yang jujur; tidak ada data mesin contoh (Design.md §6).
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
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
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
            .width(232.dp)
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
            val selected = destination == current
            Text(
                text = stringResource(destination.titleRes),
                color = if (selected) Palette.onSurface else Palette.onSurfaceMuted,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onSelect(destination) },
                    ),
            )
        }
    }
}

@Composable
private fun DestinationContent(
    destination: AppDestination,
    modifier: Modifier = Modifier,
) {
    if (destination == AppDestination.OPERATION) {
        OperationScreen(modifier = modifier)
        return
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(destination.titleRes),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                if (destination == AppDestination.OPERATION) {
                    R.string.state_operation_blocked
                } else {
                    R.string.state_not_configured
                },
            ),
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
