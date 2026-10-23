package id.pemilah.telemetry

import id.pemilah.domain.waste.WasteClass

/**
 * Event siklus untuk backend statistik (`.work/Schema.md` §9, PRD `COM-07`).
 *
 * `motionStatus` dan `graspStatus` sengaja dipisah (OUT-01): gerakan selesai
 * bukan bukti objek terambil.
 */
data class TelemetryEvent(
    val eventId: String,
    val schemaVersion: Int,
    val sessionId: String,
    val eventType: EventType,
    val capturedAtEpochMs: Long,
    val payload: CyclePayload,
) {
    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

enum class EventType { CYCLE, DETECTION }

data class CyclePayload(
    val targetId: String,
    val wasteClass: WasteClass,
    val confidence: Double,
    val blockId: String,
    val robotId: String,
    val binId: String,
    val motionStatus: MotionStatus,
    val graspStatus: GraspStatus,
    val verificationMethod: VerificationMethod,
)

/** Hasil gerakan robot. Bukan hasil pengambilan. */
enum class MotionStatus { COMPLETED, FAILED, CANCELLED }

/** Hasil pengambilan objek. */
enum class GraspStatus { VERIFIED, UNVERIFIED, FAILED }

enum class VerificationMethod { NONE, SENSOR, VISUAL }

/**
 * Aturan pembentukan payload: `graspStatus == VERIFIED` hanya sah bila ada
 * metode verifikasi selain [VerificationMethod.NONE] (Schema.md §9, OUT-01).
 */
sealed interface PayloadResult {
    data class Valid(val payload: CyclePayload) : PayloadResult
    data class Invalid(val reason: PayloadProblem) : PayloadResult
}

enum class PayloadProblem {
    VERIFIED_WITHOUT_METHOD,
    METHOD_WITHOUT_VERIFICATION,
}

object CyclePayloadFactory {

    fun create(
        targetId: String,
        wasteClass: WasteClass,
        confidence: Double,
        blockId: String,
        robotId: String,
        binId: String,
        motionStatus: MotionStatus,
        graspStatus: GraspStatus,
        verificationMethod: VerificationMethod,
    ): PayloadResult {
        if (graspStatus == GraspStatus.VERIFIED && verificationMethod == VerificationMethod.NONE) {
            return PayloadResult.Invalid(PayloadProblem.VERIFIED_WITHOUT_METHOD)
        }
        if (verificationMethod != VerificationMethod.NONE && graspStatus != GraspStatus.VERIFIED) {
            return PayloadResult.Invalid(PayloadProblem.METHOD_WITHOUT_VERIFICATION)
        }
        return PayloadResult.Valid(
            CyclePayload(
                targetId = targetId,
                wasteClass = wasteClass,
                confidence = confidence,
                blockId = blockId,
                robotId = robotId,
                binId = binId,
                motionStatus = motionStatus,
                graspStatus = graspStatus,
                verificationMethod = verificationMethod,
            ),
        )
    }
}
