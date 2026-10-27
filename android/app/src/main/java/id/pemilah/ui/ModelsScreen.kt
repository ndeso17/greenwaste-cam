package id.pemilah.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ProfileImportProblem
import id.pemilah.model.ProfileImportProblemCode

/**
 * Layar Models: daftar profil model tersimpan, label kelas, alasan penolakan,
 * dan penanda profil aktif (UI-02, MOD-01, MOD-02).
 *
 * Aturan yang dijaga:
 * - Seluruh teks berasal dari `strings.xml` (D-03); tidak ada literal di dalam
 *   Composable.
 * - Status validasi ditampilkan sebagai teks + ikon, bukan warna saja
 *   (`Design.md` §7).
 * - Tidak ada data contoh: layar kosong berarti belum ada profil (`Design.md` §6).
 * - Alasan penolakan berasal dari `ProfileProblem`/`ProfileJsonProblem` lewat
 *   [ModelsUiState]; layar tidak menyimpulkan sendiri sebab kegagalan.
 * - Tombol impor hanya mengirim URI terpilih ke pemanggil; pembacaan berkas
 *   dilakukan di luar Composable supaya tidak ada I/O pada frame komposisi
 *   (`Styleguide.md` §4).
 */
@Composable
fun ModelsScreen(
    state: ModelsUiState,
    onProfileFilePicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    importProblems: List<ProfileImportProblem> = emptyList(),
    onThresholdTextChange: (String, String) -> Unit = { _, _ -> },
    onThresholdCommit: (String) -> Unit = {},
    onGroupingToggle: (String, Boolean) -> Unit = { _, _ -> },
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // `null` berarti operator membatalkan pemilih dokumen; itu bukan galat.
        uri?.let { onProfileFilePicked(it.toString()) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.models_title),
            color = Palette.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Button(onClick = { picker.launch(PROFILE_MIME_TYPES) }) {
            Text(text = stringResource(R.string.models_action_import))
        }
        if (importProblems.isNotEmpty()) {
            ImportProblems(importProblems)
        }
        when {
            state.loading -> Text(
                text = stringResource(R.string.models_loading),
                color = Palette.onSurfaceMuted,
                fontSize = 14.sp,
            )

            state.isEmpty -> EmptyModelsState()
            else -> ProfilesList(
                state = state,
                onThresholdTextChange = onThresholdTextChange,
                onThresholdCommit = onThresholdCommit,
                onGroupingToggle = onGroupingToggle,
            )
        }
    }
}

/** Filter pemilih dokumen: hanya berkas JSON profil yang ditawarkan (UI-02). */
private val PROFILE_MIME_TYPES = arrayOf("application/json")

@Composable
private fun ImportProblems(problems: List<ProfileImportProblem>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        problems.forEach { problem ->
            Text(
                text = stringResource(problem.code.messageRes()),
                color = Palette.danger,
                fontSize = 13.sp,
            )
        }
    }
}

@StringRes
private fun ProfileImportProblemCode.messageRes(): Int = when (this) {
    ProfileImportProblemCode.UNREADABLE_FILE -> R.string.models_import_unreadable
    ProfileImportProblemCode.EMPTY_FILE -> R.string.models_import_empty
    ProfileImportProblemCode.FILE_TOO_LARGE -> R.string.models_import_too_large
    ProfileImportProblemCode.INVALID_JSON -> R.string.models_import_invalid_json
    ProfileImportProblemCode.INVALID_PROFILE -> R.string.models_import_invalid_profile
    ProfileImportProblemCode.STORAGE_FAILED -> R.string.models_import_storage_failed
}

@Composable
private fun EmptyModelsState() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.models_empty_title),
            color = Palette.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.models_empty_hint),
            color = Palette.onSurfaceMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ProfilesList(
    state: ModelsUiState,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
    onGroupingToggle: (String, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.activeAbsentReason?.let { reason ->
            Text(
                text = stringResource(reason.toMessageRes()),
                color = Palette.warning,
                fontSize = 14.sp,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.rows, key = { row -> row.fileName }) { row ->
                ProfileRow(
                    state = state,
                    row = row,
                    onThresholdTextChange = onThresholdTextChange,
                    onThresholdCommit = onThresholdCommit,
                    onGroupingToggle = onGroupingToggle,
                )
            }
        }
    }
}

@Composable
private fun ProfileRow(
    state: ModelsUiState,
    row: ModelsProfileRow,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
    onGroupingToggle: (String, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.surfaceRaised, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = row.status.icon(),
                contentDescription = null,
                tint = row.status.tint(),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = row.name ?: stringResource(R.string.models_name_unreadable),
                color = Palette.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (row.isActive) {
                // Penanda aktif memakai teks, bukan hanya warna (Design.md §7).
                Text(
                    text = stringResource(R.string.models_active_badge),
                    color = Palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        row.version?.let { version ->
            Text(
                text = stringResource(R.string.models_version, version),
                color = Palette.onSurfaceMuted,
                fontSize = 13.sp,
            )
        }
        Text(
            text = stringResource(row.status.messageRes()),
            color = row.status.tint(),
            fontSize = 13.sp,
        )
        if (row.labels.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.models_labels_title),
                    color = Palette.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                row.labels.forEach { label -> LabelLine(label) }
            }
        }
        if (row.problems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                row.problems.forEach { problem -> ProblemLine(problem) }
            }
        }
        ThresholdEditor(
            state = state,
            row = row,
            onThresholdTextChange = onThresholdTextChange,
            onThresholdCommit = onThresholdCommit,
        )
        GroupingToggle(row = row, onGroupingToggle = onGroupingToggle)
    }
}

/**
 * Penyunting ambang keyakinan (MOD-04). Nilai awal berasal dari profil tersimpan
 * lewat [ModelsUiState.thresholdTextOf]; bentuk persen ditampilkan bersama nilai
 * pecahan tanpa membulatkan diam-diam. Alasan penolakan tampil pada field ini,
 * bukan sebagai pesan umum.
 */
@Composable
private fun ThresholdEditor(
    state: ModelsUiState,
    row: ModelsProfileRow,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
) {
    val text = state.thresholdTextOf(row.profileId).orEmpty()
    val percent = ModelsNumbers.parseFraction(text)?.let { ModelsNumbers.toPercentText(it) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.models_threshold_label),
            color = Palette.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { value -> onThresholdTextChange(row.profileId, value) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = percent?.let { stringResource(R.string.models_threshold_percent, it) }
                ?: stringResource(R.string.models_threshold_hint),
            color = Palette.onSurfaceMuted,
            fontSize = 12.sp,
        )
        state.thresholdProblems[row.profileId]?.forEach { problem -> ProblemLine(problem) }
        Button(onClick = { onThresholdCommit(row.profileId) }) {
            Text(text = stringResource(R.string.models_threshold_apply))
        }
    }
}

/**
 * Sakelar aturan kelompok (MOD-06). Nilainya tersimpan per profil, tetapi
 * keterangan menyatakan bahwa perilaku pengelompokan belum ditetapkan (B-07),
 * sehingga sakelar tidak mengklaim perilaku yang belum ada.
 */
@Composable
private fun GroupingToggle(row: ModelsProfileRow, onGroupingToggle: (String, Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.models_grouping_label),
                color = Palette.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.models_grouping_note),
                color = Palette.onSurfaceMuted,
                fontSize = 12.sp,
            )
        }
        Switch(
            checked = row.groupingEnabled == true,
            enabled = row.groupingEnabled != null,
            onCheckedChange = { enabled -> onGroupingToggle(row.profileId, enabled) },
        )
    }
}

@Composable
private fun LabelLine(label: ModelsLabelUi) {
    val text = when {
        label.wasteClass == null -> stringResource(R.string.models_label_unmapped, label.label)
        label.bin == null -> stringResource(
            R.string.models_label_without_bin,
            label.label,
            label.wasteClass.name,
        )

        else -> stringResource(
            R.string.models_label_with_bin,
            label.label,
            label.wasteClass.name,
            label.bin,
        )
    }
    Text(
        text = text,
        color = if (label.wasteClass == null) Palette.danger else Palette.onSurfaceMuted,
        fontSize = 13.sp,
    )
}

@Composable
private fun ProblemLine(problem: ModelsProblemUi) {
    val field = problem.field.ifEmpty { stringResource(R.string.models_problem_field_unknown) }
    Text(
        text = stringResource(
            R.string.models_problem_line,
            field,
            stringResource(problem.code.messageRes()),
        ),
        color = Palette.danger,
        fontSize = 13.sp,
    )
}

@StringRes
private fun ModelsProfileStatus.messageRes(): Int = when (this) {
    ModelsProfileStatus.VALID -> R.string.models_status_valid
    ModelsProfileStatus.INVALID -> R.string.models_status_invalid
    ModelsProfileStatus.UNREADABLE -> R.string.models_status_unreadable
}

private fun ModelsProfileStatus.icon(): ImageVector = when (this) {
    ModelsProfileStatus.VALID -> Icons.Rounded.CheckCircle
    ModelsProfileStatus.INVALID -> Icons.Rounded.WarningAmber
    ModelsProfileStatus.UNREADABLE -> Icons.AutoMirrored.Rounded.HelpOutline
}

private fun ModelsProfileStatus.tint(): Color = when (this) {
    ModelsProfileStatus.VALID -> Palette.success
    ModelsProfileStatus.INVALID -> Palette.warning
    ModelsProfileStatus.UNREADABLE -> Palette.danger
}

@StringRes
private fun ModelsProblemCode.messageRes(): Int = when (this) {
    ModelsProblemCode.JSON_INVALID -> R.string.models_reason_json_invalid
    ModelsProblemCode.MISSING_FIELD -> R.string.models_reason_missing_field
    ModelsProblemCode.UNKNOWN_SCHEMA_VERSION -> R.string.models_reason_unknown_schema_version
    ModelsProblemCode.INVALID_VALUE -> R.string.models_reason_invalid_value
    ModelsProblemCode.UNKNOWN_WASTE_CLASS -> R.string.models_reason_unknown_waste_class
    ModelsProblemCode.NO_LABELS -> R.string.models_reason_no_labels
    ModelsProblemCode.DUPLICATE_LABEL -> R.string.models_reason_duplicate_label
    ModelsProblemCode.LABEL_NOT_MAPPED -> R.string.models_reason_label_not_mapped
    ModelsProblemCode.MAPPING_FOR_UNKNOWN_LABEL -> R.string.models_reason_mapping_for_unknown_label
    ModelsProblemCode.COMBINED_KALENG_BESI_LABEL -> R.string.models_reason_combined_label
    ModelsProblemCode.NO_LOCATION_OUTPUT -> R.string.models_reason_no_location_output
    ModelsProblemCode.INVALID_CONFIDENCE_THRESHOLD -> R.string.models_reason_invalid_threshold
    ModelsProblemCode.INVALID_INPUT_SIZE -> R.string.models_reason_invalid_input_size
    ModelsProblemCode.UNKNOWN_NORMALIZATION -> R.string.models_reason_unknown_normalization
    ModelsProblemCode.CLASS_NOT_IN_PROFILE -> R.string.models_reason_class_not_in_profile
    ModelsProblemCode.INVALID_BIN_NAME -> R.string.models_reason_invalid_bin_name
}

@StringRes
private fun ActiveProfileAbsentReason.toMessageRes(): Int = when (this) {
    ActiveProfileAbsentReason.NOT_SELECTED -> R.string.models_active_not_selected
    ActiveProfileAbsentReason.FILE_MISSING -> R.string.models_active_file_missing
    ActiveProfileAbsentReason.NOT_VALID -> R.string.models_active_not_valid
}

@Preview(widthDp = 960, heightDp = 540)
@Composable
private fun ModelsScreenEmptyPreview() {
    ModelsScreen(state = ModelsUiState(), onProfileFilePicked = {})
}
