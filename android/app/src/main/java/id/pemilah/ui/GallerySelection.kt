package id.pemilah.ui

import id.pemilah.storage.CorrectionTarget
import id.pemilah.storage.SampleRecord

/**
 * Pilihan jamak pada Galeri (instruksi pengguna 27 Oktober 2026).
 *
 * Satu model pilihan dipakai untuk dua tingkat sekaligus:
 * - gambar terpilih penuh ([sampleIds]) berarti seluruh objeknya ikut terpilih;
 * - objek terpilih sebagian disimpan per gambar ([objects]).
 *
 * Logika ini murni supaya dapat diuji tanpa Compose maupun penyimpanan.
 */
data class GallerySelection(
    val sampleIds: Set<String> = emptySet(),
    val objects: Map<String, Set<String>> = emptyMap(),
) {
    fun isSampleSelected(sampleId: String): Boolean = sampleId in sampleIds

    fun isObjectSelected(sampleId: String, objectId: String): Boolean =
        isSampleSelected(sampleId) || objects[sampleId]?.contains(objectId) == true

    /** Pilih/batal satu gambar; membatalkan gambar juga membatalkan objek di dalamnya. */
    fun toggleSample(sampleId: String): GallerySelection =
        if (isSampleSelected(sampleId)) {
            copy(sampleIds = sampleIds - sampleId, objects = objects - sampleId)
        } else {
            copy(sampleIds = sampleIds + sampleId, objects = objects - sampleId)
        }

    /**
     * Pilih/batal satu objek. Bila seluruh objek gambar terpilih, gambar dianggap
     * terpilih penuh supaya jumlah pilihan tetap konsisten saat daftar berubah.
     */
    fun toggleObject(sampleId: String, objectId: String, allObjectIds: Set<String>): GallerySelection {
        if (isSampleSelected(sampleId)) {
            // Membatalkan satu objek dari gambar yang terpilih penuh: sisanya
            // menjadi pilihan sebagian.
            val remaining = allObjectIds - objectId
            val updatedObjects = if (remaining.isEmpty()) {
                objects - sampleId
            } else {
                objects + (sampleId to remaining)
            }
            return copy(sampleIds = sampleIds - sampleId, objects = updatedObjects)
        }
        val current = objects[sampleId].orEmpty()
        val next = if (objectId in current) current - objectId else current + objectId
        val updatedObjects = if (next.isEmpty()) objects - sampleId else objects + (sampleId to next)
        val full = next.isNotEmpty() && next.containsAll(allObjectIds)
        return copy(
            sampleIds = if (full) sampleIds + sampleId else sampleIds,
            objects = if (full) updatedObjects - sampleId else updatedObjects,
        )
    }

    fun selectAll(records: List<SampleRecord>): GallerySelection =
        copy(sampleIds = records.map { it.id }.toSet(), objects = emptyMap())

    fun clear(): GallerySelection = GallerySelection()

    fun selectedObjectCount(records: List<SampleRecord>): Int = records.sumOf { record ->
        when {
            isSampleSelected(record.id) -> record.objects.size
            else -> objects[record.id].orEmpty().count { id -> record.objects.any { it.id == id } }
        }
    }

    /** Target koreksi massal; objek yang sudah tidak ada di daftar diabaikan. */
    fun correctionTargets(records: List<SampleRecord>): List<CorrectionTarget> = buildList {
        records.forEach { record ->
            val ids = if (isSampleSelected(record.id)) {
                record.objects.map { it.id }
            } else {
                objects[record.id].orEmpty().filter { id -> record.objects.any { it.id == id } }
            }
            ids.forEach { add(CorrectionTarget(record.id, it)) }
        }
    }

    fun selectedSampleIds(records: List<SampleRecord>): List<String> =
        records.map { it.id }.filter { id -> isSampleSelected(id) || objects[id].orEmpty().isNotEmpty() }

    val isEmpty: Boolean get() = sampleIds.isEmpty() && objects.isEmpty()
}
