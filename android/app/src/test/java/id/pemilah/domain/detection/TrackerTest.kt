package id.pemilah.domain.detection

import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private object TrackerFixture {
    const val MIN_IOU = 0.3
    const val MAX_MISSED_FRAMES = 2
    const val FIRST_FRAME_MS = 1_700_000_000_000L
    const val FRAME_INTERVAL_MS = 40L

    val config = TrackingConfig(
        minIouToMatch = MIN_IOU,
        maxMissedFramesBeforeRelease = MAX_MISSED_FRAMES,
    )

    fun box(left: Double, top: Double, size: Double = 100.0) =
        Box(left = left, top = top, right = left + size, bottom = top + size)

    fun detection(
        box: Box,
        frameId: Long = 1L,
        capturedAtEpochMs: Long = FIRST_FRAME_MS,
        wasteClass: WasteClass? = WasteClass.PLASTIK,
        confidence: Double = 0.9,
    ) = Detection(
        detectionId = "det-$frameId-${box.left.toInt()}",
        frameId = frameId,
        capturedAtEpochMs = capturedAtEpochMs,
        box = box,
        label = "Plastik",
        wasteClass = wasteClass,
        confidence = confidence,
        blockId = "A1",
    )

    fun timeOf(frame: Long): Long = FIRST_FRAME_MS + frame * FRAME_INTERVAL_MS
}

class TrackerTest {

    @Test
    fun update_objectMovingSlightly_keepsSameTrackId() {
        val tracker = Tracker(TrackerFixture.config)
        val first = tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )

        val second = tracker.update(
            listOf(
                TrackerFixture.detection(
                    TrackerFixture.box(10.0, 0.0),
                    frameId = 2,
                    capturedAtEpochMs = TrackerFixture.timeOf(1),
                ),
            ),
            TrackerFixture.timeOf(1),
        )

        assertEquals(1, first.active.size)
        assertEquals(first.active.single().trackId, second.active.single().trackId)
    }

    @Test
    fun update_twoAdjacentObjects_areNotMergedIntoOneTrack() {
        val tracker = Tracker(TrackerFixture.config)

        val snapshot = tracker.update(
            listOf(
                TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1),
                TrackerFixture.detection(TrackerFixture.box(100.0, 0.0), frameId = 1),
            ),
            TrackerFixture.timeOf(0),
        )

        assertEquals(2, snapshot.active.size)
        assertNotEquals(snapshot.active[0].trackId, snapshot.active[1].trackId)
    }

    @Test
    fun update_twoAdjacentObjects_nextFrame_idsStayStablePerObject() {
        val tracker = Tracker(TrackerFixture.config)
        val firstFrame = tracker.update(
            listOf(
                TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1),
                TrackerFixture.detection(TrackerFixture.box(100.0, 0.0), frameId = 1),
            ),
            TrackerFixture.timeOf(0),
        )
        val leftId = firstFrame.active.first { it.box.left == 0.0 }.trackId
        val rightId = firstFrame.active.first { it.box.left == 100.0 }.trackId

        val secondFrame = tracker.update(
            listOf(
                TrackerFixture.detection(
                    TrackerFixture.box(102.0, 0.0),
                    frameId = 2,
                    capturedAtEpochMs = TrackerFixture.timeOf(1),
                ),
                TrackerFixture.detection(
                    TrackerFixture.box(2.0, 0.0),
                    frameId = 2,
                    capturedAtEpochMs = TrackerFixture.timeOf(1),
                ),
            ),
            TrackerFixture.timeOf(1),
        )

        assertEquals(
            leftId,
            secondFrame.active.first { it.box.left < 50.0 }.trackId,
        )
        assertEquals(
            rightId,
            secondFrame.active.first { it.box.left > 50.0 }.trackId,
        )
    }

    @Test
    fun update_newObjectFarFromExisting_getsNewTrackId() {
        val tracker = Tracker(TrackerFixture.config)
        val first = tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )

        val second = tracker.update(
            listOf(
                TrackerFixture.detection(
                    TrackerFixture.box(500.0, 500.0),
                    frameId = 2,
                    capturedAtEpochMs = TrackerFixture.timeOf(1),
                ),
            ),
            TrackerFixture.timeOf(1),
        )

        val original = second.tracks.first { it.trackId == first.active.single().trackId }
        val fresh = second.tracks.first { it.trackId != original.trackId }

        assertNotEquals(first.active.single().trackId, fresh.trackId)
        assertTrue(original.missedFrames >= 1)
    }

    @Test
    fun update_missingFrameBelowLimit_trackStaysActive() {
        val tracker = Tracker(TrackerFixture.config)
        tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )

        val snapshot = tracker.update(emptyList(), TrackerFixture.timeOf(1))

        assertEquals(1, snapshot.active.size)
        assertEquals(1, snapshot.active.single().missedFrames)
    }

    @Test
    fun update_missingFramesReachLimit_trackIsReleased() {
        val tracker = Tracker(TrackerFixture.config)
        tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )

        tracker.update(emptyList(), TrackerFixture.timeOf(1))
        val snapshot = tracker.update(emptyList(), TrackerFixture.timeOf(2))

        assertTrue(snapshot.active.isEmpty())
        assertEquals(TrackState.RELEASED, snapshot.tracks.single().state)
    }

    @Test
    fun update_detectionReappearsAfterRelease_getsNewTrackId() {
        val tracker = Tracker(TrackerFixture.config)
        val first = tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )
        tracker.update(emptyList(), TrackerFixture.timeOf(1))
        tracker.update(emptyList(), TrackerFixture.timeOf(2))

        val snapshot = tracker.update(
            listOf(
                TrackerFixture.detection(
                    TrackerFixture.box(0.0, 0.0),
                    frameId = 4,
                    capturedAtEpochMs = TrackerFixture.timeOf(3),
                ),
            ),
            TrackerFixture.timeOf(3),
        )

        assertNotEquals(first.active.single().trackId, snapshot.active.single().trackId)
    }

    @Test
    fun purgeReleased_removesReleasedTracksOnly() {
        val tracker = Tracker(TrackerFixture.config)
        tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )
        tracker.update(emptyList(), TrackerFixture.timeOf(1))
        tracker.update(emptyList(), TrackerFixture.timeOf(2))

        val snapshot = tracker.purgeReleased()

        assertTrue(snapshot.tracks.isEmpty())
    }

    @Test
    fun update_lastSeenTime_followsDetectionTimeNotWallClock() {
        val tracker = Tracker(TrackerFixture.config)
        tracker.update(
            listOf(TrackerFixture.detection(TrackerFixture.box(0.0, 0.0), frameId = 1)),
            TrackerFixture.timeOf(0),
        )

        val snapshot = tracker.update(
            listOf(
                TrackerFixture.detection(
                    TrackerFixture.box(5.0, 0.0),
                    frameId = 2,
                    capturedAtEpochMs = TrackerFixture.timeOf(1),
                ),
            ),
            TrackerFixture.timeOf(1),
        )

        assertEquals(TrackerFixture.timeOf(1), snapshot.active.single().lastSeenEpochMs)
        assertEquals(TrackerFixture.timeOf(0), snapshot.active.single().firstSeenEpochMs)
    }

    @Test
    fun box_iou_identicalBoxes_isOne() {
        val box = TrackerFixture.box(0.0, 0.0)

        assertEquals(1.0, box.iou(box), 1e-9)
    }

    @Test
    fun box_iou_touchingEdges_isZero() {
        val left = TrackerFixture.box(0.0, 0.0)
        val right = TrackerFixture.box(100.0, 0.0)

        assertEquals(0.0, left.iou(right), 1e-9)
    }

    @Test
    fun box_iou_halfOverlap_isOneThird() {
        val first = TrackerFixture.box(0.0, 0.0)
        val second = TrackerFixture.box(50.0, 0.0)

        assertEquals(1.0 / 3.0, first.iou(second), 1e-9)
    }
}
