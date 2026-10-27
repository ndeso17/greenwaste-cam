package id.pemilah.storage

import id.pemilah.inference.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** All methods are called on an IO/analyzer worker; metadata is the commit marker. */
class SampleStore(private val directory: File) {
    private val json=Json { ignoreUnknownKeys=false }
    @Synchronized fun list(): List<SampleRecord> {
        if(!directory.exists()) return emptyList()
        return directory.listFiles().orEmpty().filter { it.extension=="json" && it.name!="settings.json" }.map { file ->
            val record=json.decodeFromString<SampleRecord>(file.readText())
            validate(record)
            require(imageFile(record.id).isFile) { "SAMPLE_IMAGE_MISSING: ${record.id}" }
            record
        }.sortedByDescending { it.capturedAt }
    }
    @Synchronized fun save(record: SampleRecord, image: ByteArray) {
        validate(record); require(image.isNotEmpty())
        directory.mkdirs()
        val imageFile=imageFile(record.id)
        require(!imageFile.exists() && !metadata(record.id).exists()) { "SAMPLE_ALREADY_EXISTS" }
        try { atomicWrite(imageFile,image); atomicWrite(metadata(record.id),json.encodeToString(record).toByteArray()) }
        catch(e: Exception) { imageFile.delete(); throw e }
    }
    @Synchronized fun correct(id: String,objectId: String,label: String,allowedLabels: Set<String>) {
        val result = correctMany(listOf(CorrectionTarget(id, objectId)), label, allowedLabels,
            System.currentTimeMillis())
        require(result.isComplete) { "CORRECTION_FAILED: ${result.failed.joinToString()}" }
    }

    /**
     * Koreksi banyak objek dalam satu panggilan (instruksi pengguna 27 Okt 2026).
     *
     * Label tak sah menolak seluruh panggilan sebelum ada berkas ditulis. Kegagalan
     * per gambar dilaporkan di [BatchResult.failed] dan tidak menghentikan gambar
     * lain; satu gambar ditulis sekali sehingga koreksi tidak pernah separuh.
     */
    @Synchronized fun correctMany(
        targets: List<CorrectionTarget>,
        label: String,
        allowedLabels: Set<String>,
        at: Long,
    ): BatchResult {
        require(label in allowedLabels) { "INVALID_CORRECTION_LABEL" }
        require(targets.isNotEmpty()) { "NO_CORRECTION_TARGET" }
        val failed = mutableListOf<String>()
        var applied = 0
        targets.groupBy { it.sampleId }.forEach { (sampleId, group) ->
            val record = try { read(sampleId) } catch (e: Exception) { null }
            val wanted = group.map { it.objectId }.toSet()
            if (record == null) {
                failed += wanted
                return@forEach
            }
            if (wanted.any { id -> record.objects.none { it.id == id } }) {
                // Objek yang hilang menandakan daftar atau gambar sudah basi; koreksi
                // sebagian pada gambar yang sama ditolak agar hasilnya tidak separuh.
                failed += group.map { it.objectId }
                return@forEach
            }
            val updated = record.copy(objects = record.objects.map { obj ->
                if (obj.id in wanted) obj.copy(correctedLabel = label, correctedAt = at) else obj
            })
            try {
                atomicWrite(metadata(sampleId), json.encodeToString(updated).toByteArray())
                applied += wanted.size
            } catch (e: Exception) {
                failed += wanted
            }
        }
        return BatchResult(applied, failed)
    }
    @Synchronized fun delete(id: String) {
        // Rename metadata out of the published namespace before deleting the image.
        val meta=metadata(id)
        if(!meta.exists()) return
        val tombstone=File(directory,"$id.deleted")
        check(meta.renameTo(tombstone)) { "DELETE_FAILED" }
        if(imageFile(id).exists() && !imageFile(id).delete()) {
            check(tombstone.renameTo(meta)) { "DELETE_ROLLBACK_FAILED" }; error("DELETE_FAILED")
        }
        tombstone.delete()
    }
    /** Hapus banyak gambar; gambar yang gagal dilaporkan tanpa menghentikan sisanya. */
    @Synchronized fun deleteMany(ids: List<String>): BatchResult {
        val failed = mutableListOf<String>()
        var applied = 0
        ids.distinct().forEach { id ->
            // Daftar Galeri bisa basi: gambar yang sudah tidak ada dilaporkan, bukan
            // dihitung sebagai berhasil.
            if (!metadata(id).exists()) { failed += id; return@forEach }
            try { delete(id); applied++ } catch (e: Exception) { failed += id }
        }
        return BatchResult(applied, failed)
    }
    @Synchronized fun snapshot(): CorrectionSnapshot {
        val references=list().flatMap { record -> record.objects.mapNotNull { obj ->
            obj.correctedLabel?.let { CorrectionReference(record.modelId,it,obj.descriptor) }
        } }
        return CorrectionSnapshot(references,threshold())
    }
    @Synchronized fun threshold(): Float {
        val f=File(directory,"similarity.txt")
        if(!f.exists()) return 0.97f
        return f.readText().toFloat().also { require(it.isFinite() && it in 0.8f..1f) }
    }
    @Synchronized fun setThreshold(value: Float) {
        require(value.isFinite() && value in 0.8f..1f)
        directory.mkdirs(); atomicWrite(File(directory,"similarity.txt"),value.toString().toByteArray())
    }
    fun imageFile(id: String): File { safeId(id); return File(directory,"$id.jpg") }
    private fun metadata(id: String): File { safeId(id); return File(directory,"$id.json") }
    private fun read(id: String)=json.decodeFromString<SampleRecord>(metadata(id).readText()).also { validate(it) }
    private fun safeId(id: String) { require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "INVALID_SAMPLE_ID" } }
    private fun validate(record: SampleRecord) {
        safeId(record.id)
        require(record.schemaVersion==1 && record.width>0 && record.height>0) { "SAMPLE_SCHEMA_INVALID" }
        require(record.objects.map { it.id }.distinct().size==record.objects.size)
        record.objects.forEach { o ->
            require(listOf(o.left,o.top,o.right,o.bottom,o.probability).all { it.isFinite() && it in 0f..1f })
            require(o.right>o.left && o.bottom>o.top)
            require(o.descriptor.size==CorrectionMatcher.DESCRIPTOR_SIZE && o.descriptor.all { it.isFinite() && it in 0f..1f })
            require((o.correctedLabel==null)==(o.correctedAt==null))
        }
    }
    private fun atomicWrite(file: File, bytes: ByteArray) {
        val temporary=File(directory,"${file.name}.${UUID.randomUUID()}.tmp")
        try {
            temporary.outputStream().use { stream -> stream.write(bytes); stream.fd.sync() }
            check(temporary.renameTo(file)) { "WRITE_FAILED" }
        } finally { temporary.delete() }
    }
}
