package id.pemilah.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import id.pemilah.domain.operation.MachineState
import id.pemilah.model.ProfileImport
import id.pemilah.model.ProfileImportProblem
import id.pemilah.model.ProfileRepository
import id.pemilah.model.ProfileSwitchBlock
import id.pemilah.storage.ConfigStore
import id.pemilah.storage.ContentProfileReader
import id.pemilah.storage.FileConfigStorage

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
    val context = LocalContext.current
    // Satu sesi Models untuk seluruh aplikasi: layar Models dan layar Operasi
    // membaca profil aktif dari sumber yang sama (Architecture.md §4).
    val modelsSession = remember(context) {
        val repository = ProfileRepository(ConfigStore(FileConfigStorage(context.applicationContext)))
        ModelsSession(repository, ProfileImport(repository))
    }
    val profileFileReader = remember(context) { ContentProfileReader(context.applicationContext) }
    var modelsState by remember(modelsSession) { mutableStateOf(modelsSession.load()) }
    var importProblems by remember { mutableStateOf(emptyList<ProfileImportProblem>()) }
    var switchBlocks by remember { mutableStateOf(emptyList<ProfileSwitchBlock>()) }

    fun applyOutcome(outcome: ModelsSessionOutcome) {
        modelsState = outcome.state
        importProblems = outcome.messages
            .filterIsInstance<ModelsSessionMessage.ImportRejected>()
            .flatMap { it.problems }
        switchBlocks = outcome.messages
            .filterIsInstance<ModelsSessionMessage.SwitchRejected>()
            .flatMap { it.blocks }
    }
    // rememberSaveable: lipat tidak berubah saat konfigurasi ulang (D-UI-02 butir 5).
    // Boolean disimpan langsung karena Compose dapat menyimpan tipe primitif tanpa
    // Saver khusus; NavigationRailState tetap menjadi kontrak yang diuji.
    var railExpanded by rememberSaveable { mutableStateOf(NavigationRailState().expanded) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        // On compact windows the expanded rail overlays content instead of squeezing the shutter.
        val reservedWidth = if (maxWidth < 600.dp || !railExpanded) {
            NavigationRailWidths.collapsed
        } else {
            NavigationRailWidths.expanded
        }
        Row(Modifier.fillMaxSize()) {
            Spacer(Modifier.width(reservedWidth))
            DestinationContent(
                destination = current,
                modelsState = modelsState,
                importProblems = importProblems,
                switchBlocks = switchBlocks,
                profileLabel = modelsSession.operationProfileLabel(modelsState),
                onProfileFilePicked = { uri ->
                    applyOutcome(modelsSession.importProfile(uri, profileFileReader))
                },
                onThresholdTextChange = { profileId, text ->
                    modelsState = modelsSession.draftThreshold(profileId, text)
                },
                onThresholdCommit = { profileId ->
                    applyOutcome(
                        modelsSession.commitThreshold(profileId, modelsState.thresholdTextOf(profileId).orEmpty()),
                    )
                },
                onGroupingToggle = { profileId, enabled ->
                    applyOutcome(modelsSession.toggleGrouping(profileId, enabled))
                },
                onActivateProfile = { profileId ->
                    // Pemilik state operasi belum membagikan status berjalan pada
                    // tahap ini; guard tetap dijalankan dengan masukan sebenarnya
                    // dari pemanggil dan diuji dengan matriks penuh pada
                    // `ModelsSessionTest`.
                    applyOutcome(
                        modelsSession.setActiveProfile(
                            profileId = profileId,
                            machineState = MachineState.Stopped,
                            hasActiveTarget = false,
                        ),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        NavigationRail(
            current = current,
            expanded = railExpanded,
            onToggle = { railExpanded = NavigationRailState(railExpanded).toggle().expanded },
            onSelect = { current = it },
        )
    }
}

@Composable
private fun NavigationRail(
    current: AppDestination,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (AppDestination) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (expanded) NavigationRailWidths.expanded else NavigationRailWidths.collapsed)
            .background(Palette.surfaceRaised)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            // Tertutup: tombol di tengah agar sejajar dengan ikon tujuan di
            // bawahnya. Terbuka: judul mengisi sisa lebar, tombol di kanan.
            horizontalArrangement = if (expanded) Arrangement.End else Arrangement.Center,
        ) {
            if (expanded) {
                Text(
                    text = stringResource(R.string.app_title),
                    color = Palette.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                )
            }
            // Tombol lipat sebaris dengan judul aplikasi, di puncak rail dan di
            // luar daftar tujuan yang dapat digulir (D-UI-02 butir 4, UI-07).
            RailToggleButton(expanded = expanded, onToggle = onToggle)
        }
        // Hanya daftar tujuan yang dapat digulir, sehingga tombol lipat di atas
        // tidak pernah keluar layar.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AppDestination.entries.forEach { destination ->
                NavigationRailItem(
                    destination = destination,
                    selected = destination == current,
                    expanded = expanded,
                    onClick = { onSelect(destination) },
                )
            }
        }
    }
}

/**
 * Tombol lipat di puncak rail (UI-07). Selalu terlihat pada kedua keadaan dan
 * tidak ikut tergulir bersama daftar tujuan.
 */
@Composable
private fun RailToggleButton(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(48.dp),
    ) {
        Icon(
            imageVector = if (expanded) NavigationIcons.collapse else NavigationIcons.expand,
            contentDescription = stringResource(
                if (expanded) R.string.nav_collapse else R.string.nav_expand,
            ),
            tint = Palette.onSurfaceMuted,
            modifier = Modifier.size(24.dp),
        )
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
    val label = stringResource(destination.titleRes)
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
            // Deskripsi aksesibilitas memakai label yang terlihat, termasuk saat
            // rail tertutup: nama aksesibilitas wajib memuat teks terlihat
            // (WCAG 2.5.3), supaya Voice Access "tap Models" tetap cocok.
            .semantics(mergeDescendants = true) {
                contentDescription = label
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
                text = label,
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
    modelsState: ModelsUiState,
    importProblems: List<ProfileImportProblem>,
    switchBlocks: List<ProfileSwitchBlock>,
    profileLabel: String?,
    onProfileFilePicked: (String) -> Unit,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
    onGroupingToggle: (String, Boolean) -> Unit,
    onActivateProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination == AppDestination.OPERATION) {
        // Profil aktif disalurkan sebagai state awal supaya layar Operasi
        // menampilkan nama dan versi tanpa memulai operasi (UI-01, T-038).
        OperationScreen(
            modifier = modifier,
            state = rememberDefaultOperationState()
                .copy(profileNameAndVersion = profileLabel),
        )
        return
    }
    if (destination == AppDestination.MODELS) {
        ModelsScreen(
            state = modelsState,
            onProfileFilePicked = onProfileFilePicked,
            modifier = modifier,
            importProblems = importProblems,
            onThresholdTextChange = onThresholdTextChange,
            onThresholdCommit = onThresholdCommit,
            onGroupingToggle = onGroupingToggle,
            onActivateProfile = onActivateProfile,
            switchBlocks = switchBlocks,
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
