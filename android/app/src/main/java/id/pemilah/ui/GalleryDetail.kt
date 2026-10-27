package id.pemilah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.pemilah.R
import id.pemilah.storage.SampleObject
import id.pemilah.storage.SampleRecord
import id.pemilah.storage.SampleStore

/**
 * Detail satu gambar: kotak + titik bantu bernomor, koreksi per objek, dan
 * pemilihan objek untuk aksi massal. Nomor pada titik sama dengan nomor objek
 * supaya operator tidak menebak objek mana yang sedang dikoreksi.
 */
@Composable
internal fun GalleryDetail(
    store: SampleStore,
    record: SampleRecord,
    selection: GallerySelection,
    busy: Boolean,
    onBack: () -> Unit,
    onToggleObject: (String) -> Unit,
    onCorrect: (objectId: String, label: String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusObjectId by remember(record.id) { mutableStateOf<String?>(null) }
    val selectedIds = record.objects.filter { selection.isObjectSelected(record.id, it.id) }.map { it.id }.toSet()
    TextButton(onClick = onBack) { Text(stringResource(R.string.gallery_back)) }
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GalleryImage(
            store = store,
            record = record,
            // Let aspectRatio choose a width that fits the height cap. Forcing the
            // full width as well makes portrait content overflow its measured bounds.
            modifier = Modifier.heightIn(max = 320.dp).align(Alignment.CenterHorizontally),
            selectedObjectIds = selectedIds,
            focusObjectId = focusObjectId,
            onObjectClick = { objectId ->
                // Ketukan pada titik memilih objek sekaligus memusatkannya, sehingga
                // aksi massal dan koreksi memakai objek yang sama.
                focusObjectId = objectId
                onToggleObject(objectId)
            },
        )
        Text(stringResource(R.string.gallery_object_hint))
        record.objects.forEachIndexed { index, obj ->
            ObjectCorrection(
                number = index + 1,
                record = record,
                obj = obj,
                focused = obj.id == focusObjectId,
                selected = obj.id in selectedIds,
                busy = busy,
                onSelect = { onToggleObject(obj.id) },
                onFocus = { focusObjectId = obj.id },
                onConfirm = { label -> onCorrect(obj.id, label) },
            )
        }
        TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.gallery_delete)) }
    }
}

@Composable
private fun ObjectCorrection(
    number: Int,
    record: SampleRecord,
    obj: SampleObject,
    focused: Boolean,
    selected: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onFocus: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(record.id, obj.id, obj.correctedLabel) {
        mutableStateOf(obj.correctedLabel ?: obj.predictedLabel)
    }
    var editing by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onSelect() }, enabled = !busy)
                Text(
                    stringResource(R.string.gallery_object_number, number, obj.predictedLabel, (obj.probability * 100).toInt()),
                )
            }
            Text(
                if (obj.correctedLabel == null) {
                    stringResource(R.string.gallery_unreviewed)
                } else {
                    stringResource(R.string.gallery_confirmed, obj.correctedLabel)
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { editing = true; onFocus() }, enabled = !busy) {
                    Text(stringResource(R.string.gallery_correct))
                }
                if (obj.correctedLabel == null) {
                    TextButton(onClick = { onConfirm(obj.predictedLabel) }, enabled = !busy && obj.predictedLabel in record.labels) {
                        Text(stringResource(R.string.gallery_confirm_prediction))
                    }
                }
            }
        }
    }
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.gallery_correct_title, number)) },
            text = {
                Column {
                    Text(stringResource(R.string.gallery_object_hint))
                    LazyColumn {
                        items(record.labels) { label ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                RadioButton(selected = draft == label, onClick = { draft = label })
                                Text(label, modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(draft); editing = false }, enabled = !busy && draft in record.labels) {
                    Text(stringResource(R.string.gallery_save_correction))
                }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.gallery_cancel)) } },
        )
    }
}
