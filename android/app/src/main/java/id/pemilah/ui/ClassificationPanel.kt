package id.pemilah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.pemilah.Palette
import id.pemilah.R
import id.pemilah.inference.ClassifierFailureCode

/**
 * Panel uji klasifikasi di layar Operasi (D-13, instruksi pengguna 27 Okt 2026).
 *
 * Panel ini menjawab pertanyaan "apakah model benar-benar dipakai?". Isinya
 * hanya hasil inferensi nyata: tidak ada label atau angka contoh, dan saat
 * pemantauan berhenti tidak ada hasil lama yang dipamerkan seolah masih berlaku.
 */
@Composable
fun ClassificationPanel(
    state: OperationUiState,
    classification: ClassificationUi,
    modifier: Modifier = Modifier,
) {
    MachinePanel(title = stringResource(R.string.classification_title), modifier = modifier) {
        if (!state.isRunning) {
            PlaceholderText(text = stringResource(R.string.classification_stopped))
            return@MachinePanel
        }
        when (classification) {
            ClassificationUi.Waiting -> PlaceholderText(
                text = stringResource(R.string.classification_waiting),
            )

            is ClassificationUi.Result -> ClassificationResult(result = classification)

            is ClassificationUi.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PanelField(
                        label = stringResource(R.string.classification_model),
                        value = classification.profileName,
                    )
                    PlaceholderText(
                        text = stringResource(classification.failure.code.messageRes()),
                        tone = StatusTone.DANGER,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassificationResult(result: ClassificationUi.Result) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = result.label,
            color = if (result.belowThreshold) Palette.warning else Palette.success,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.statusDescription(
                "${stringResource(R.string.classification_title)}: ${result.label}",
            ),
        )
        PanelField(
            label = stringResource(R.string.classification_confidence),
            value = ModelsNumbers.toPercentText(result.probability.toDouble()),
        )
        PanelField(
            label = stringResource(R.string.classification_threshold),
            value = ModelsNumbers.toPercentText(result.threshold),
        )
        if (result.belowThreshold) {
            PlaceholderText(
                text = stringResource(R.string.classification_below_threshold),
                tone = StatusTone.WARNING,
            )
        }
        PanelField(
            label = stringResource(R.string.classification_model),
            value = result.profileName,
        )
        result.runnerUp.firstOrNull()?.let { (label, probability) ->
            PanelField(
                label = stringResource(R.string.classification_runner_up),
                value = "$label ${ModelsNumbers.toPercentText(probability.toDouble())}",
            )
        }
    }
}

private fun ClassifierFailureCode.messageRes(): Int = when (this) {
    ClassifierFailureCode.MODEL_FILE_MISSING -> R.string.classification_failed_missing_model
    ClassifierFailureCode.MODEL_UNREADABLE -> R.string.classification_failed_unreadable_model
    ClassifierFailureCode.OUTPUT_SHAPE_MISMATCH -> R.string.classification_failed_output_shape
    ClassifierFailureCode.RUN_FAILED -> R.string.classification_failed_run
}