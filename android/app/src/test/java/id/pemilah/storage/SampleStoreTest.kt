package id.pemilah.storage

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SampleStoreTest {
    private fun record()=SampleRecord(id="one",sessionId="session",modelId="model",modelName="model",capturedAt=1,width=20,height=20,objects=listOf("a","b").map {
        SampleObject(it,0f,0f,1f,1f,"Kaca",0.7f,List(192){0.5f})
    })
    @Test fun correctionRoundTripAndDeleteArePerObject() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            val store=SampleStore(root); store.save(record(),byteArrayOf(1))
            assertTrue(store.snapshot().references.isEmpty())
            store.correct("one","b","Plastik",setOf("Plastik","Kaca"))
            val reloaded=SampleStore(root)
            val objects=reloaded.list().single().objects
            assertNull(objects[0].correctedLabel)
            assertEquals("Kaca",objects[1].predictedLabel)
            assertEquals("Plastik",objects[1].correctedLabel)
            assertEquals(1,reloaded.snapshot().references.size)
            reloaded.delete("one")
            assertTrue(reloaded.snapshot().references.isEmpty())
            assertFalse(reloaded.imageFile("one").exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun corruptMetadataAndFailedWritesAreReported() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            java.io.File(root,"bad.json").writeText("broken")
            assertTrue(runCatching { SampleStore(root).list() }.isFailure)
            val occupied=java.io.File(root,"occupied").apply { writeText("file") }
            assertTrue(runCatching { SampleStore(occupied).save(record(),byteArrayOf(1)) }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidIdCannotEscapeStorage() {
        val root=Files.createTempDirectory("samples").toFile()
        try { assertTrue(runCatching { SampleStore(root).imageFile("../outside") }.isFailure) }
        finally { root.deleteRecursively() }
    }
    @Test fun correctMany_writesEverySelectedObjectOnce() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            val store=SampleStore(root)
            store.save(record(),byteArrayOf(1))
            store.save(record().copy(id="two",capturedAt=2),byteArrayOf(1))
            val result=store.correctMany(
                listOf(CorrectionTarget("one","a"),CorrectionTarget("two","b")),
                "Plastik",setOf("Plastik","Kaca"),at=99L)
            assertTrue(result.isComplete)
            assertEquals(2,result.applied)
            val reloaded=SampleStore(root).list()
            assertEquals("Plastik",reloaded.first { it.id=="one" }.objects[0].correctedLabel)
            assertEquals("Plastik",reloaded.first { it.id=="two" }.objects[1].correctedLabel)
            assertNull(reloaded.first { it.id=="one" }.objects[1].correctedLabel)
            assertEquals(99L,reloaded.first { it.id=="one" }.objects[0].correctedAt)
        } finally { root.deleteRecursively() }
    }
    @Test fun correctMany_invalidLabelWritesNothing() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            val store=SampleStore(root); store.save(record(),byteArrayOf(1))
            assertTrue(runCatching {
                store.correctMany(listOf(CorrectionTarget("one","a")),"Besi",setOf("Plastik","Kaca"),at=1L)
            }.isFailure)
            assertTrue(SampleStore(root).snapshot().references.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun correctMany_missingObjectFailsOnlyThatSample() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            val store=SampleStore(root); store.save(record(),byteArrayOf(1))
            val result=store.correctMany(
                listOf(CorrectionTarget("one","a"),CorrectionTarget("one","missing")),
                "Plastik",setOf("Plastik","Kaca"),at=1L)
            assertFalse(result.isComplete)
            // Seluruh target pada gambar itu dilaporkan gagal karena gambarnya ditolak utuh.
            assertEquals(listOf("a","missing"),result.failed)
            assertEquals(0,result.applied)
            assertTrue(SampleStore(root).snapshot().references.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun deleteMany_reportsMissingIdAndDeletesTheRest() {
        val root=Files.createTempDirectory("samples").toFile()
        try {
            val store=SampleStore(root)
            store.save(record(),byteArrayOf(1))
            store.save(record().copy(id="two",capturedAt=2),byteArrayOf(1))
            val result=store.deleteMany(listOf("one","absent","two"))
            assertEquals(1,result.failed.size)
            assertEquals(2,result.applied)
            assertTrue(SampleStore(root).list().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
