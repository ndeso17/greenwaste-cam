package id.pemilah.inference

import kotlin.math.abs

/** Conservative visual reference; not training and not a calibrated probability. */
data class CorrectionReference(val modelId: String, val label: String, val descriptor: List<Float>)
data class CorrectionMatch(val label: String, val similarity: Float)
data class CorrectionSnapshot(val references: List<CorrectionReference> = emptyList(), val threshold: Float = 0.97f)

object CorrectionMatcher {
    const val DESCRIPTOR_SIZE = 8 * 8 * 3
    fun descriptor(frame: ArgbFrame): List<Float> = buildList {
        for(y in 0..7) for(x in 0..7) {
            val sx=((x+0.5f)*frame.width/8).toInt().coerceIn(0,frame.width-1)
            val sy=((y+0.5f)*frame.height/8).toInt().coerceIn(0,frame.height-1)
            val pixel=frame.pixels[sy*frame.width+sx]
            for(c in 0..2) add(((pixel shr (16-c*8)) and 255)/255f)
        }
    }
    fun match(modelId: String, descriptor: List<Float>, snapshot: CorrectionSnapshot): CorrectionMatch? {
        if(descriptor.size!=DESCRIPTOR_SIZE || descriptor.any { !it.isFinite() }) return null
        val matches=snapshot.references.filter {
            it.modelId==modelId && it.descriptor.size==DESCRIPTOR_SIZE && it.descriptor.all { v -> v.isFinite() }
        }.map { reference ->
            CorrectionMatch(reference.label,1f-descriptor.indices.sumOf { abs(descriptor[it]-reference.descriptor[it]).toDouble() }.toFloat()/descriptor.size)
        }.sortedByDescending { it.similarity }
        val best=matches.firstOrNull()?.takeIf { it.similarity>=snapshot.threshold } ?: return null
        // Conflicting labels in the accepted neighborhood are ambiguous, even if one is marginally closer.
        if(matches.any { it.label!=best.label && it.similarity>=snapshot.threshold }) return null
        return best
    }
}
