package id.pemilah.inference

import org.junit.Assert.*
import org.junit.Test

class ObjectDecoderTest {
    @Test fun endToEndDecoderSeparatesCupAndBottleAndRejectsSceneSurface() {
        val rows=arrayOf(
            floatArrayOf(80f,160f,240f,480f,0.9f,41f),
            floatArrayOf(400f,160f,560f,480f,0.8f,39f),
            floatArrayOf(0f,0f,640f,640f,0.7f,60f),
            floatArrayOf(81f,160f,240f,480f,0.6f,41f))
        val boxes=ObjectDecoder.decodeEndToEnd(rows,640,640)
        assertEquals(2,boxes.size)
        assertEquals(0.125f,boxes[0].left,0.001f)
        assertTrue(boxes[0].right < boxes[1].left)
    }

    @Test fun rotationKeepsObjectPixelsAlignedWithPortraitFrame() {
        val f=ArgbFrame(intArrayOf(1,2,3,4,5,6),3,2)
        val rotated=ObjectDecoder.rotate(f,90)
        assertEquals(2,rotated.width)
        assertEquals(3,rotated.height)
        assertArrayEquals(intArrayOf(4,1,5,2,6,3),rotated.pixels)
        assertArrayEquals(f.pixels,ObjectDecoder.rotate(rotated,270).pixels)
    }

    @Test fun twoObjectsAndDuplicateAreDecodedInOriginalCoordinates() {
        val output = Array(17) { FloatArray(3) }
        fun box(i: Int, x: Float, y: Float, w: Float, h: Float, score: Float) {
            output[0][i]=x; output[1][i]=y; output[2][i]=w; output[3][i]=h; output[4][i]=score
        }
        box(0,160f,240f,160f,80f,0.9f)
        box(1,161f,240f,160f,80f,0.8f)
        box(2,480f,320f,80f,80f,0.7f)
        val boxes=ObjectDecoder.decode(output,1280,640)
        assertEquals(2,boxes.size)
        assertEquals(0.125f,boxes[0].left,0.001f)
        assertEquals(0.125f,boxes[0].top,0.001f)
    }
    @Test fun invalidAndPaddingOnlyBoxesAreIgnored() {
        assertTrue(ObjectDecoder.decode(emptyArray(),100,100).isEmpty())
        val output=Array(17){floatArrayOf(0f)}
        output[0][0]=Float.NaN; output[4][0]=0.9f
        assertTrue(ObjectDecoder.decode(output,100,100).isEmpty())
    }
    @Test fun letterboxPreservesEdgesAndCropUsesWholeBox() {
        val f=ArgbFrame(IntArray(8){it},4,2)
        val cropped=ObjectDecoder.crop(f,NormalizedRegion(0.5f,0f,1f,1f))
        assertArrayEquals(intArrayOf(2,3,6,7),cropped.pixels)
        val tensor=ObjectDecoder.letterbox(f,4)
        assertEquals(48,tensor.size)
        assertEquals(114/255f,tensor[0],0.001f)
    }
}
