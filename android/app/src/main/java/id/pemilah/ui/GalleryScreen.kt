package id.pemilah.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import id.pemilah.R
import id.pemilah.storage.BatchResult
import id.pemilah.storage.CorrectionTarget
import id.pemilah.storage.SampleRecord
import id.pemilah.storage.SampleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Galeri sampel Uji Model: koreksi per objek, pilih jamak, dan hapus massal
 * (instruksi pengguna 27 Oktober 2026). Semua I/O berjalan di [Dispatchers.IO];
 * Composable hanya menerima state dan lambda.
 */
@Composable
fun GalleryScreen(store: SampleStore, modifier: Modifier = Modifier) {
    var records by remember { mutableStateOf<List<SampleRecord>>(emptyList()) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(GallerySelection()) }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<List<String>?>(null) }
    var labelTargets by remember { mutableStateOf<List<CorrectionTarget>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var batchNotice by remember { mutableStateOf<BatchNotice?>(null) }
    var threshold by remember { mutableStateOf(0.97f) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        records = withContext(Dispatchers.IO) { store.list() }
        threshold = withContext(Dispatchers.IO) { store.threshold() }
        // Pilihan mengikuti daftar terbaru supaya id yang sudah dihapus tidak tersisa.
        selection = GallerySelection(
            sampleIds = selection.sampleIds.intersect(records.map { it.id }.toSet()),
            objects = selection.objects.filterKeys { key -> records.any { it.id == key } },
        )
    }
    fun perform(action: suspend () -> Unit = {}) {
        scope.launch {
            busy = true; error = null; batchNotice = null
            try { action(); reload() } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
            finally { busy = false }
        }
    }
    fun reportBatch(result: BatchResult) {
        batchNotice = BatchNotice.of(result)
    }
    LaunchedEffect(store) {
        busy = true
        try { reload() } catch (e: Exception) { error = e.message } finally { busy = false }
    }
    val selected = records.firstOrNull { it.id == selectedId }
    val selectedObjectCount = selection.selectedObjectCount(records)
    val selectedSamples = selection.selectedSampleIds(records)

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.menu_gallery), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = {
                selectMode = !selectMode
                if (!selectMode) selection = selection.clear()
            }) {
                Text(stringResource(if (selectMode) R.string.gallery_selection_cancel else R.string.gallery_selection_start))
            }
        }
        error?.let { message ->
            Text(stringResource(R.string.gallery_error, message), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { perform() }, enabled = !busy) {
                Text(stringResource(R.string.gallery_retry))
            }
        }
        batchNotice?.let { notice ->
            when (notice) {
                is BatchNotice.Applied -> Text(
                    text = stringResource(R.string.gallery_batch_applied, notice.count),
                    color = MaterialTheme.colorScheme.primary,
                )
                is BatchNotice.Failed -> Text(
                    text = stringResource(R.string.gallery_batch_failed, notice.ids.joinToString()),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (selected == null) {
            if (selectMode) {
                BatchBar(
                    sampleCount = selectedSamples.size,
                    objectCount = selectedObjectCount,
                    busy = busy,
                    onSelectAll = { selection = selection.selectAll(records) },
                    onClear = { selection = selection.clear() },
                    onLabel = { labelTargets = selection.correctionTargets(records) },
                    onDelete = { pendingDelete = selectedSamples },
                )
            }
            Text(stringResource(R.string.gallery_reference_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.gallery_similarity, (threshold * 100).toInt()))
            Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 0.8f..1f, enabled = !busy)
            TextButton(
                onClick = {
                    val value = threshold
                    perform { withContext(Dispatchers.IO) { store.setThreshold(value) } }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.gallery_apply_similarity)) }
            if (records.isEmpty() && !busy) Text(stringResource(R.string.gallery_empty))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(records, key = { it.id }) { record ->
                    val checked = selection.isSampleSelected(record.id)
                    OutlinedCard(
                        onClick = {
                            if (selectMode) selection = selection.toggleSample(record.id) else selectedId = record.id
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (selectMode) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { selection = selection.toggleSample(record.id) },
                                )
                            }
                            GalleryImage(store, record, Modifier.width(100.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(
                                        R.string.gallery_sample, record.modelName, record.objects.size,
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(record.capturedAt)),
                                    ),
                                )
                                Text(
                                    stringResource(
                                        if (record.objects.all { it.correctedLabel != null }) {
                                            R.string.gallery_confirmed
                                        } else {
                                            R.string.gallery_unreviewed
                                        },
                                        record.objects.mapNotNull { it.correctedLabel }.distinct().joinToString(),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            GalleryDetail(
                store = store,
                record = selected,
                selection = selection,
                busy = busy,
                onBack = { selectedId = null },
                onToggleObject = { objectId ->
                    selection = selection.toggleObject(
                        selected.id, objectId, selected.objects.map { it.id }.toSet(),
                    )
                },
                onCorrect = { objectId, label ->
                    perform {
                        withContext(Dispatchers.IO) {
                            store.correct(selected.id, objectId, label, selected.labels.toSet())
                        }
                    }
                },
                onDelete = { pendingDelete = listOf(selected.id) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    pendingDelete?.let { ids ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.gallery_batch_delete_title, ids.size)) },
            text = {
                Text(
                    stringResource(
                        R.string.gallery_batch_delete_body,
                        ids.size,
                        records.filter { it.id in ids }.sumOf { it.objects.size },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    perform {
                        val result = withContext(Dispatchers.IO) { store.deleteMany(ids) }
                        selection = selection.clear()
                        selectedId = null
                        reportBatch(result)
                    }
                }) { Text(stringResource(R.string.gallery_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.gallery_cancel)) } },
        )
    }
    labelTargets?.let { targets ->
        BatchLabelDialog(
            records = records,
            targets = targets,
            busy = busy,
            onDismiss = { labelTargets = null },
            onApply = { label, applicable ->
                labelTargets = null
                perform {
                    val result = withContext(Dispatchers.IO) {
                        store.correctMany(applicable, label, records.flatMap { it.labels }.toSet(), System.currentTimeMillis())
                    }
                    selection = selection.clear()
                    reportBatch(result)
                }
            },
        )
    }
}

@Composable
private fun BatchBar(
    sampleCount: Int,
    objectCount: Int,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onLabel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gallery_selection_count, sampleCount, objectCount))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onSelectAll, enabled = !busy) {
                Text(stringResource(R.string.gallery_selection_all))
            }
            TextButton(onClick = onClear, enabled = !busy && (sampleCount > 0 || objectCount > 0)) {
                Text(stringResource(R.string.gallery_selection_clear))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onLabel, enabled = !busy && objectCount > 0) {
                Text(stringResource(R.string.gallery_batch_label))
            }
            TextButton(onClick = onDelete, enabled = !busy && sampleCount > 0) {
                Text(stringResource(R.string.gallery_batch_delete))
            }
        }
    }
}

/**
 * Pemilih label untuk koreksi massal. Hanya objek dari model yang benar-benar
 * memuat label terpilih yang ikut dikirim, supaya tidak ada label asing tersimpan.
 */
@Composable
private fun BatchLabelDialog(
    records: List<SampleRecord>,
    targets: List<CorrectionTarget>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (label: String, targets: List<CorrectionTarget>) -> Unit,
) {
    val labels = records.flatMap { it.labels }.distinct().sorted()
    var draft by remember(targets) { mutableStateOf(labels.firstOrNull()) }
    val applicable = targets.filter { target ->
        val record = records.firstOrNull { it.id == target.sampleId } ?: return@filter false
        draft != null && draft in record.labels
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gallery_batch_label_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.gallery_batch_label_body, applicable.size, targets.size))
                LazyColumn {
                    items(labels) { label ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            androidx.compose.material3.RadioButton(
                                selected = draft == label,
                                onClick = { draft = label },
                            )
                            Text(label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { draft?.let { onApply(it, applicable) } },
                enabled = !busy && draft != null && applicable.isNotEmpty(),
            ) { Text(stringResource(R.string.gallery_save_correction)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.gallery_cancel)) } },
    )
}
