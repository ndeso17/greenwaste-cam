package id.pemilah.domain.detection

import id.pemilah.domain.waste.WasteClass

/** Kotak pada koordinat frame asal. Tidak pernah dinormalisasi di lapisan ini. */
data class Box(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val area: Double get() = if (width <= 0.0 || height <= 0.0) 0.0 else width * height

    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    fun intersection(other: Box): Double {
        val overlapWidth = minOf(right, other.right) - maxOf(left, other.left)
        val overlapHeight = minOf(bottom, other.bottom) - maxOf(top, other.top)
        return if (overlapWidth <= 0.0 || overlapHeight <= 0.0) 0.0 else overlapWidth * overlapHeight
    }

    fun iou(other: Box): Double {
        val intersection = intersection(other)
        val union = area + other.area - intersection
        return if (union <= 0.0) 0.0 else intersection / union
    }
}

/** Satu deteksi hasil inferensi (`.work/Schema.md` §6, CAM-02). */
data class Detection(
    val detectionId: String,
    val frameId: Long,
    val capturedAtEpochMs: Long,
    val box: Box,
    val label: String,
    val wasteClass: WasteClass?,
    val confidence: Double,
    val blockId: String?,
)
