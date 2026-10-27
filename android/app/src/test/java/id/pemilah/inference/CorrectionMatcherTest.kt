package id.pemilah.inference

import org.junit.Assert.*
import org.junit.Test

class CorrectionMatcherTest {
    private val descriptor=CorrectionMatcher.descriptor(ArgbFrame(IntArray(4){0xff00ff00.toInt()},2,2))
    @Test fun correctionMatchesOnlySameModel() {
        val snapshot=CorrectionSnapshot(listOf(CorrectionReference("one","Plastik",descriptor)))
        assertEquals("Plastik",CorrectionMatcher.match("one",descriptor,snapshot)?.label)
        assertNull(CorrectionMatcher.match("two",descriptor,snapshot))
    }
    @Test fun conflictingLabelsAreRejected() {
        val snapshot=CorrectionSnapshot(listOf(CorrectionReference("one","Plastik",descriptor),CorrectionReference("one","Kaca",descriptor)))
        assertNull(CorrectionMatcher.match("one",descriptor,snapshot))
    }
    @Test fun removedReferencesAndUnrelatedImagesDoNotMatch() {
        assertNull(CorrectionMatcher.match("one",descriptor,CorrectionSnapshot()))
        val other=CorrectionMatcher.descriptor(ArgbFrame(IntArray(4){0xffff0000.toInt()},2,2))
        assertNull(CorrectionMatcher.match("one",other,CorrectionSnapshot(listOf(CorrectionReference("one","Plastik",descriptor)))))
    }
}
