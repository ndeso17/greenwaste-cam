package id.pemilah.inference

import kotlin.math.*

/** YOLOv8 [4+classes, proposals], class-agnostic NMS for location proposals only. */
object ObjectDecoder {
    fun decode(output: Array<FloatArray>, width: Int, height: Int,
        threshold: Float = 0.25f, iouThreshold: Float = 0.45f): List<NormalizedRegion> {
        if (width <= 0 || height <= 0 || output.size < 5) return emptyList()
        val count=output[0].size
        if (output.any { it.size != count }) return emptyList()
        val scale=min(640f/width,640f/height)
        val padX=((640-round(width*scale).toInt())/2).toFloat()
        val padY=((640-round(height*scale).toInt())/2).toFloat()
        val candidates=(0 until count).mapNotNull { i ->
            val score=(4 until output.size).maxOf { output[it][i] }
            val x=output[0][i]; val y=output[1][i]; val w=output[2][i]; val h=output[3][i]
            if (!listOf(score,x,y,w,h).all { it.isFinite() } || score<threshold || w<=0 || h<=0) return@mapNotNull null
            val box=NormalizedRegion(
                ((x-w/2-padX)/scale/width).coerceIn(0f,1f),
                ((y-h/2-padY)/scale/height).coerceIn(0f,1f),
                ((x+w/2-padX)/scale/width).coerceIn(0f,1f),
                ((y+h/2-padY)/scale/height).coerceIn(0f,1f))
            if(box.right<=box.left || box.bottom<=box.top) null else box to score
        }.sortedByDescending { it.second }
        val selected=mutableListOf<NormalizedRegion>()
        for((box,_) in candidates) {
            if(selected.none { iou(it,box)>iouThreshold }) selected+=box
            if(selected.size>=32) break
        }
        return selected
    }
    /** YOLOv10 end-to-end rows [left, top, right, bottom, score, COCO class]. */
    fun decodeEndToEnd(rows: Array<FloatArray>, width: Int, height: Int,
        threshold: Float = 0.25f): List<NormalizedRegion> {
        if (width <= 0 || height <= 0) return emptyList()
        val scale=min(640f/width,640f/height)
        val padX=((640-round(width*scale).toInt())/2).toFloat()
        val padY=((640-round(height*scale).toInt())/2).toFloat()
        // People and fixed scene furniture are not proposals for material classification.
        val backgroundClasses=setOf(0,56,57,59,60,61)
        val candidates=rows.mapNotNull { row ->
            if(row.size!=6 || row.any { !it.isFinite() } || row[4]<threshold || row[5].toInt() in backgroundClasses) return@mapNotNull null
            val box=NormalizedRegion(
                ((row[0]-padX)/scale/width).coerceIn(0f,1f),
                ((row[1]-padY)/scale/height).coerceIn(0f,1f),
                ((row[2]-padX)/scale/width).coerceIn(0f,1f),
                ((row[3]-padY)/scale/height).coerceIn(0f,1f))
            if(box.right<=box.left || box.bottom<=box.top) null else box to row[4]
        }.sortedByDescending { it.second }
        val selected=mutableListOf<NormalizedRegion>()
        for((box,_) in candidates) {
            if(selected.none { iou(it,box)>0.45f }) selected+=box
            if(selected.size>=32) break
        }
        return selected
    }
    private fun iou(a: NormalizedRegion,b: NormalizedRegion): Float {
        val intersection=max(0f,min(a.right,b.right)-max(a.left,b.left))*max(0f,min(a.bottom,b.bottom)-max(a.top,b.top))
        val union=(a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection
        return if(union>0f) intersection/union else 0f
    }
    fun rotate(frame: ArgbFrame, degrees: Int): ArgbFrame {
        val rotation=((degrees%360)+360)%360
        if(rotation==0) return frame
        require(rotation in listOf(90,180,270))
        val width=if(rotation==180) frame.width else frame.height
        val height=if(rotation==180) frame.height else frame.width
        val pixels=IntArray(frame.pixels.size)
        for(y in 0 until frame.height) for(x in 0 until frame.width) {
            val dx=when(rotation) {90->frame.height-1-y;180->frame.width-1-x;else->y}
            val dy=when(rotation) {90->x;180->frame.height-1-y;else->frame.width-1-x}
            pixels[dy*width+dx]=frame.pixels[y*frame.width+x]
        }
        return ArgbFrame(pixels,width,height,frame.capturedAtMillis)
    }
    fun crop(frame: ArgbFrame, box: NormalizedRegion): ArgbFrame {
        val l=floor(box.left*frame.width).toInt().coerceIn(0,frame.width-1)
        val t=floor(box.top*frame.height).toInt().coerceIn(0,frame.height-1)
        val r=ceil(box.right*frame.width).toInt().coerceIn(l+1,frame.width)
        val b=ceil(box.bottom*frame.height).toInt().coerceIn(t+1,frame.height)
        val pixels=IntArray((r-l)*(b-t))
        for(y in t until b) System.arraycopy(frame.pixels,y*frame.width+l,pixels,(y-t)*(r-l),r-l)
        return ArgbFrame(pixels,r-l,b-t,frame.capturedAtMillis)
    }
    fun letterbox(frame: ArgbFrame, size: Int=640): FloatArray {
        val scale=min(size.toFloat()/frame.width,size.toFloat()/frame.height)
        val w=round(frame.width*scale).toInt(); val h=round(frame.height*scale).toInt()
        val left=(size-w)/2; val top=(size-h)/2
        val tensor=FloatArray(3*size*size){114/255f}
        for(y in 0 until h) for(x in 0 until w) {
            val pixel=frame.pixels[min(frame.height-1,(y/scale).toInt())*frame.width+min(frame.width-1,(x/scale).toInt())]
            for(c in 0..2) tensor[c*size*size+(y+top)*size+x+left]=((pixel shr (16-c*8)) and 255)/255f
        }
        return tensor
    }
}
