package id.pemilah.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.model.ActiveProfileAbsentReason

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
 */
@Composable
fun ModelsScreen(
    state: ModelsUiState,
    modifier: Modifier = Modifier,
) {
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
        when {
            state.loading -> Text(
                text = stringResource(R.string.models_loading),
                color = Palette.onSurfaceMuted,
                fontSize = 14.sp,
            )

            state.isEmpty -> EmptyModelsState()
            else -> ProfilesList(state)
        }
    }
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
private fun ProfilesList(state: ModelsUiState) {
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
                ProfileRow(row)
            }
        }
    }
}

@Composable
private fun ProfileRow(row: ModelsProfileRow) {
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
    ModelsScreen(state = ModelsUiState())
}
