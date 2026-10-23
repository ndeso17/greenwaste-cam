package id.pemilah.telemetry

import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private object TelemetryFixture {
    const val EVENT_ID = "event-1"
    const val SESSION_ID = "session-1"
    const val CAPTURED_AT_MS = 1_700_000_000_000L
    const val CONFIDENCE = 0.9
    const val BLOCK_ID = "A1"
    const val ROBOT_ID = "left"
    const val BIN_ID = "B2"
    const val TARGET_ID = "target-1"

    fun event(eventId: String = EVENT_ID) = TelemetryEvent(
        eventId = eventId,
        schemaVersion = TelemetryEvent.SCHEMA_VERSION,
        sessionId = SESSION_ID,
        eventType = EventType.CYCLE,
        capturedAtEpochMs = CAPTURED_AT_MS,
        payload = validPayload(),
    )

    fun validPayload(): CyclePayload {
        val result = CyclePayloadFactory.create(
            targetId = TARGET_ID,
            wasteClass = WasteClass.PLASTIK,
            confidence = CONFIDENCE,
            blockId = BLOCK_ID,
            robotId = ROBOT_ID,
            binId = BIN_ID,
            motionStatus = MotionStatus.COMPLETED,
            graspStatus = GraspStatus.UNVERIFIED,
            verificationMethod = VerificationMethod.NONE,
        )
        return (result as PayloadResult.Valid).payload
    }

    class FakeSender(private val fails: Boolean = false) : TelemetrySender {
        val delivered = mutableListOf<String>()

        override fun send(event: TelemetryEvent): Boolean {
            if (fails) return false
            delivered.add(event.eventId)
            return true
        }
    }

    class ThrowingSender : TelemetrySender {
        override fun send(event: TelemetryEvent): Boolean = error("backend tidak terjangkau")
    }
}

class TelemetryQueueTest {

    @Test
    fun enqueue_backendDown_eventIsStored() {
        val queue = TelemetryQueue(InMemoryTelemetryStore(), TelemetryFixture.FakeSender(fails = true))

        queue.enqueue(TelemetryFixture.event())

        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun enqueue_sameEventIdTwice_storesOnce() {
        val queue = TelemetryQueue(InMemoryTelemetryStore(), TelemetryFixture.FakeSender())

        queue.enqueue(TelemetryFixture.event())
        queue.enqueue(TelemetryFixture.event())

        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun flush_backendUp_deliversAndClears() {
        val sender = TelemetryFixture.FakeSender()
        val queue = TelemetryQueue(InMemoryTelemetryStore(), sender)
        queue.enqueue(TelemetryFixture.event())

        val report = queue.flush()

        assertEquals(FlushReport(sent = 1, remaining = 0, failedEventId = null), report)
        assertEquals(listOf(TelemetryFixture.EVENT_ID), sender.delivered)
    }

    @Test
    fun flush_failedSend_keepsEventWithSameId() {
        val queue = TelemetryQueue(
            InMemoryTelemetryStore(),
            TelemetryFixture.FakeSender(fails = true),
        )
        queue.enqueue(TelemetryFixture.event())

        val report = queue.flush()

        assertEquals(TelemetryFixture.EVENT_ID, report.failedEventId)
        assertEquals(1, report.remaining)
        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun flush_retryAfterFailure_reusesEventId() {
        val store = InMemoryTelemetryStore()
        val failing = TelemetryQueue(store, TelemetryFixture.FakeSender(fails = true))
        failing.enqueue(TelemetryFixture.event())
        failing.flush()

        val sender = TelemetryFixture.FakeSender()
        val retried = TelemetryQueue(store, sender)
        retried.flush()

        assertEquals(listOf(TelemetryFixture.EVENT_ID), sender.delivered)
    }

    @Test
    fun flush_senderThrows_doesNotPropagate() {
        val queue = TelemetryQueue(InMemoryTelemetryStore(), TelemetryFixture.ThrowingSender())
        queue.enqueue(TelemetryFixture.event())

        val report = queue.flush()

        assertNotNull(report.failedEventId)
        assertEquals(0, report.sent)
        assertEquals(1, queue.pendingCount())
    }

    @Test
    fun flush_multipleEvents_stopsAtFirstFailure() {
        val store = InMemoryTelemetryStore()
        val queue = TelemetryQueue(store, TelemetryFixture.FakeSender(fails = true))
        queue.enqueue(TelemetryFixture.event("event-1"))
        queue.enqueue(TelemetryFixture.event("event-2"))

        val report = queue.flush()

        assertEquals("event-1", report.failedEventId)
        assertEquals(2, report.remaining)
    }

    @Test
    fun create_verifiedWithoutMethod_isInvalid() {
        val result = CyclePayloadFactory.create(
            targetId = TelemetryFixture.TARGET_ID,
            wasteClass = WasteClass.PLASTIK,
            confidence = TelemetryFixture.CONFIDENCE,
            blockId = TelemetryFixture.BLOCK_ID,
            robotId = TelemetryFixture.ROBOT_ID,
            binId = TelemetryFixture.BIN_ID,
            motionStatus = MotionStatus.COMPLETED,
            graspStatus = GraspStatus.VERIFIED,
            verificationMethod = VerificationMethod.NONE,
        )

        assertEquals(
            PayloadResult.Invalid(PayloadProblem.VERIFIED_WITHOUT_METHOD),
            result,
        )
    }

    @Test
    fun create_verifiedWithSensor_isValid() {
        val result = CyclePayloadFactory.create(
            targetId = TelemetryFixture.TARGET_ID,
            wasteClass = WasteClass.PLASTIK,
            confidence = TelemetryFixture.CONFIDENCE,
            blockId = TelemetryFixture.BLOCK_ID,
            robotId = TelemetryFixture.ROBOT_ID,
            binId = TelemetryFixture.BIN_ID,
            motionStatus = MotionStatus.COMPLETED,
            graspStatus = GraspStatus.VERIFIED,
            verificationMethod = VerificationMethod.SENSOR,
        )

        assertTrue(result is PayloadResult.Valid)
    }

    @Test
    fun payload_motionCompletedDoesNotImplyGraspVerified() {
        val payload = TelemetryFixture.validPayload()

        assertEquals(MotionStatus.COMPLETED, payload.motionStatus)
        assertEquals(GraspStatus.UNVERIFIED, payload.graspStatus)
        assertEquals(VerificationMethod.NONE, payload.verificationMethod)
    }
}
