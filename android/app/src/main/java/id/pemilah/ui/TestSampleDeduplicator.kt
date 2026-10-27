package id.pemilah.ui

import id.pemilah.inference.AnalyzedObject
import id.pemilah.inference.CorrectionMatcher
import id.pemilah.inference.NormalizedRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Session-local image deduplication, independent of predicted class and robot tracking. */
internal class TestSampleDeduplicator {
    private data class Entry(
        val anchor: AnalyzedObject,
        var saved: Boolean = false,
        var missed: Int = 0,
    )

    private var entries = listOf<Entry>()
    private var visible = listOf<Entry>()

    fun reset() {
        entries = emptyList()
        visible = emptyList()
    }

    /** Call for every analyzed frame, including frames without detections. */
    fun shouldSave(objects: List<AnalyzedObject>): Boolean {
        val available = entries.toMutableList()
        visible = objects.map { obj ->
            val match = available.filter { sameAppearance(it.anchor, obj) }
                .maxByOrNull { overlap(it.anchor.region, obj.region) }
                ?.takeIf { samePosition(it.anchor.region, obj.region) }
            if (match != null) {
                available.remove(match)
                match.missed = 0
                match
            } else {
                Entry(obj)
            }
        }
        available.forEach { it.missed++ }
        entries = visible + available.filter { it.missed <= MAX_MISSED_FRAMES }
        return visible.any { !it.saved }
    }

    /** Only acknowledge after both the image and annotation have been saved successfully. */
    fun markSaved() {
        visible.forEach { it.saved = true }
    }

    private fun sameAppearance(a: AnalyzedObject, b: AnalyzedObject): Boolean {
        if (a.descriptor.size != CorrectionMatcher.DESCRIPTOR_SIZE ||
            b.descriptor.size != CorrectionMatcher.DESCRIPTOR_SIZE) return false
        val distance = a.descriptor.indices.sumOf {
            abs(a.descriptor[it] - b.descriptor[it]).toDouble()
        } / a.descriptor.size
        return distance.isFinite() && 1.0 - distance >= MIN_SIMILARITY
    }

    private fun samePosition(a: NormalizedRegion, b: NormalizedRegion): Boolean {
        val centerShiftX = abs((a.left + a.right - b.left - b.right) / 2f)
        val centerShiftY = abs((a.top + a.bottom - b.top - b.bottom) / 2f)
        return overlap(a, b) >= MIN_OVERLAP &&
            centerShiftX <= min(a.right - a.left, b.right - b.left) * MAX_CENTER_SHIFT &&
            centerShiftY <= min(a.bottom - a.top, b.bottom - b.top) * MAX_CENTER_SHIFT
    }

    private fun overlap(a: NormalizedRegion, b: NormalizedRegion): Float {
        val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
            max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val union = (a.right - a.left) * (a.bottom - a.top) +
            (b.right - b.left) * (b.bottom - b.top) - intersection
        return if (union > 0f) intersection / union else 0f
    }

    private companion object {
        // Image heuristics, not calibrated mechanical tolerances. Keep the saved anchor fixed
        // so many small movements eventually count as a new position. Phone QC showed
        // stationary crops varying below 0.97 similarity despite near-identical boxes;
        // allow camera/crop variation while still requiring both location and appearance.
        const val MIN_OVERLAP = 0.5f
        const val MIN_SIMILARITY = 0.8
        const val MAX_CENTER_SHIFT = 0.25f
        const val MAX_MISSED_FRAMES = 2
    }
}
