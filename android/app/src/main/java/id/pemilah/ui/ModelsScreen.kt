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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.model.ActiveProfileAbsentReason
import id.pemilah.model.ModelImportProblem
import id.pemilah.model.ModelImportProblemCode
import id.pemilah.model.ProfileSwitchBlock
import id.pemilah.model.ProfileSwitchBlockCode

/**
 * Layar Models: impor model ONNX, ambang keyakinan, dan informasi kelas yang
 * tertulis di dalam model (UI-02, MOD-01, MOD-02, MOD-04, D-09).
 *
 * Aturan yang dijaga:
 * - Seluruh teks berasal dari `strings.xml` (D-03); tidak ada literal di dalam
 *   Composable.
 * - Layar tidak meminta berkas konfigurasi terpisah: label, ukuran masukan, dan
 *   keluarga keluaran dibaca dari berkas `.onnx` yang diimpor.
 * - Status validasi ditampilkan sebagai teks + ikon, bukan warna saja
 *   (`Design.md` §7).
 * - Tidak ada data contoh: layar kosong berarti belum ada model (`Design.md` §6).
 * - Alasan penolakan berasal dari `ModelImportProblem`/`ProfileProblem` lewat
 *   [ModelsUiState]; layar tidak menyimpulkan sendiri sebab kegagalan.
 * - Tombol impor hanya mengirim URI terpilih ke pemanggil; pembacaan berkas
 *   dilakukan di luar Composable supaya tidak ada I/O pada frame komposisi
 *   (`Styleguide.md` §4).
 * - Tidak ada sakelar kelompok dan tidak ada tampilan bak pada layar ini sesuai
 *   instruksi pengguna 27 Oktober 2026; celah UI dicatat di `.work/FINDINGS.md`.
 */
@Composable
fun ModelsScreen(
    state: ModelsUiState,
    onModelFilePicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    importProblems: List<ModelImportProblem> = emptyList(),
    onThresholdTextChange: (String, String) -> Unit = { _, _ -> },
    onThresholdCommit: (String) -> Unit = {},
    onActivateProfile: (String) -> Unit = {},
    switchBlocks: List<ProfileSwitchBlock> = emptyList(),
) {
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        // `null` berarti operator membatalkan pemilih dokumen; itu bukan galat.
        uri?.let { onModelFilePicked(it.toString()) }
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
        Button(onClick = { picker.launch(MODEL_MIME_TYPES) }) {
            Text(text = stringResource(R.string.models_action_import))
        }
        if (importProblems.isNotEmpty()) {
            ImportProblems(importProblems)
        }
        if (switchBlocks.isNotEmpty()) {
            SwitchBlocks(switchBlocks)
        }
        when {
            state.loading -> Text(
                text = stringResource(R.string.models_loading),
                color = Palette.onSurfaceMuted,
                fontSize = 14.sp,
            )

            state.isEmpty -> EmptyModelsState()
            else -> ModelsList(
                state = state,
                onThresholdTextChange = onThresholdTextChange,
                onThresholdCommit = onThresholdCommit,
                onActivateProfile = onActivateProfile,
            )
        }
    }
}

/**
 * Alasan pergantian profil ditolak (MOD-03, MOD-04), dikembalikan
 * `ProfileSwitchGuard` lewat [ModelsSession]; layar tidak menyimpulkan sebabnya
 * sendiri.
 */
@Composable
private fun SwitchBlocks(blocks: List<ProfileSwitchBlock>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        blocks.forEach { block ->
            Text(
                text = stringResource(block.code.messageRes()),
                color = Palette.warning,
                fontSize = 13.sp,
            )
        }
    }
}

@StringRes
private fun ProfileSwitchBlockCode.messageRes(): Int = when (this) {
    ProfileSwitchBlockCode.MACHINE_RUNNING -> R.string.models_switch_running
    ProfileSwitchBlockCode.ACTIVE_TARGET_PRESENT -> R.string.models_switch_active_target
    ProfileSwitchBlockCode.PROFILE_INVALID -> R.string.models_switch_invalid_profile
    ProfileSwitchBlockCode.CONFIDENCE_THRESHOLD_UNSET -> R.string.models_switch_threshold_unset
}

/**
 * Filter pemilih dokumen.
 *
 * Penyimpanan Android tidak selalu memberi MIME `application/octet-stream`
 * untuk berkas `.onnx`, sehingga penyaringan MIME dapat menyembunyikan berkas
 * yang sah. Jenis berkas diperiksa dari nama berkas pada alur impor, bukan dari
 * penyaring pemilih (MOD-02).
 */
private val MODEL_MIME_TYPES = arrayOf("*/*")

@Composable
private fun ImportProblems(problems: List<ModelImportProblem>) {
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
private fun ModelImportProblemCode.messageRes(): Int = when (this) {
    ModelImportProblemCode.MISSING_FILE_NAME -> R.string.models_import_missing_name
    ModelImportProblemCode.NOT_ONNX_MODEL -> R.string.models_import_not_onnx
    ModelImportProblemCode.UNREADABLE_FILE -> R.string.models_import_unreadable
    ModelImportProblemCode.EMPTY_FILE -> R.string.models_import_empty
    ModelImportProblemCode.FILE_TOO_LARGE -> R.string.models_import_too_large
    ModelImportProblemCode.UNREADABLE_MODEL -> R.string.models_import_unreadable_model
    ModelImportProblemCode.MISSING_CLASS_NAMES -> R.string.models_import_missing_classes
    ModelImportProblemCode.UNSUPPORTED_INPUT_SHAPE -> R.string.models_import_unsupported_input
    ModelImportProblemCode.UNSUPPORTED_OUTPUT_SHAPE -> R.string.models_import_unsupported_output
    ModelImportProblemCode.INVALID_PROFILE -> R.string.models_import_invalid_profile
    ModelImportProblemCode.STORAGE_FAILED -> R.string.models_import_storage_failed
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
private fun ModelsList(
    state: ModelsUiState,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
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
                ModelRow(
                    state = state,
                    row = row,
                    onThresholdTextChange = onThresholdTextChange,
                    onThresholdCommit = onThresholdCommit,
                    onActivateProfile = onActivateProfile,
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    state: ModelsUiState,
    row: ModelsProfileRow,
    onThresholdTextChange: (String, String) -> Unit,
    onThresholdCommit: (String) -> Unit,
    onActivateProfile: (String) -> Unit,
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
        // Versi hanya ditampilkan bila model menuliskannya pada metadata.
        row.version?.takeIf { it.isNotBlank() }?.let { version ->
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
        if (row.classCount > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.models_classes_title, row.classCount),
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
        // Tombol aktifkan hanya muncul untuk profil yang lolos validasi; profil
        // rusak atau modelnya hilang tidak dapat diaktifkan (MOD-03).
        if (row.status == ModelsProfileStatus.VALID && !row.isActive) {
            Button(onClick = { onActivateProfile(row.profileId) }) {
                Text(text = stringResource(R.string.models_action_activate))
            }
        }
    }
}

/**
 * Penyunting ambang keyakinan (MOD-04). Nilai awal berasal dari profil tersimpan
 * lewat [ModelsUiState.thresholdTextOf]; bentuk persen ditampilkan bersama nilai
 * pecahan tanpa membulatkan diam-diam. Ambang `0.0` ditampilkan sebagai "belum
 * ditetapkan", bukan sebagai ambang sah. Alasan penolakan tampil pada field ini,
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
            text = when {
                !row.thresholdSet -> stringResource(R.string.models_threshold_unset)
                percent != null -> stringResource(R.string.models_threshold_percent, percent)
                else -> stringResource(R.string.models_threshold_hint)
            },
            color = if (row.thresholdSet) Palette.onSurfaceMuted else Palette.warning,
            fontSize = 12.sp,
        )
        state.thresholdProblems[row.profileId]?.forEach { problem -> ProblemLine(problem) }
        Button(onClick = { onThresholdCommit(row.profileId) }) {
            Text(text = stringResource(R.string.models_threshold_apply))
        }
    }
}

@Composable
private fun LabelLine(label: ModelsLabelUi) {
    val text = if (label.wasteClass == null) {
        stringResource(R.string.models_label_unmapped, label.label)
    } else {
        stringResource(R.string.models_label_mapped, label.label, label.wasteClass.name)
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
    ModelsProblemCode.MODEL_FILE_MISSING -> R.string.models_reason_model_file_missing
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
    ModelsScreen(state = ModelsUiState(), onModelFilePicked = {})
}
