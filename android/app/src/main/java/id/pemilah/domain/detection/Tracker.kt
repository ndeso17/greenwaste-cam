package id.pemilah.domain.detection

import id.pemilah.domain.waste.WasteClass

/**
 * Parameter pelacakan. Nilai ambang berasal dari konfigurasi/keputusan B-06 dan
 * tidak ditanam di kode (Rules.md §2.2); kelas ini hanya membawanya.
 */
data class TrackingConfig(
    val minIouToMatch: Double,
    val maxMissedFramesBeforeRelease: Int,
)

enum class TrackState { TRACKED, RELEASED }

data class TrackedObject(
    val trackId: String,
    val wasteClass: WasteClass?,
    val box: Box,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val missedFrames: Int,
    val state: TrackState,
) {
    val isTracked: Boolean get() = state == TrackState.TRACKED
}

data class TrackerSnapshot(val tracks: List<TrackedObject>) {
    val active: List<TrackedObject> get() = tracks.filter { it.isTracked }
}

/**
 * Pelacakan identitas objek antar frame (CAM-05, SC-07).
 *
 * Aturan yang dijaga:
 * - Setiap deteksi dipasangkan ke paling banyak satu track, dan setiap track
 *   menerima paling banyak satu deteksi; dua objek berdekatan tidak digabung.
 * - Identitas dipertahankan selama objek masih cocok secara tumpang tindih.
 * - Track yang tidak terlihat selama [TrackingConfig.maxMissedFramesBeforeRelease]
 *   frame dilepas, bukan dihapus dari riwayat snapshot.
 *
 * Kelas ini tidak memilih target (itu `TargetSelector`) dan tidak menghitung
 * posisi dunia (itu kalibrasi).
 */
class Tracker(private val config: TrackingConfig) {

    private var nextTrackNumber: Long = 1
    private var tracks: MutableList<TrackedObject> = mutableListOf()

    fun snapshot(): TrackerSnapshot = TrackerSnapshot(tracks.toList())

    /** Terapkan satu frame deteksi; `frameCapturedAtMs` adalah waktu ambil frame. */
    fun update(detections: List<Detection>, frameCapturedAtMs: Long): TrackerSnapshot {
        val unmatched = tracks.filter { it.isTracked }.toMutableList()
        val updated = mutableListOf<TrackedObject>()

        detections.forEach { detection ->
            val candidate = unmatched
                .map { track -> track to track.box.iou(detection.box) }
                .filter { (_, iou) -> iou >= config.minIouToMatch }
                .maxByOrNull { (_, iou) -> iou }
                ?.first

            if (candidate != null) {
                unmatched.remove(candidate)
                updated.add(
                    candidate.copy(
                        wasteClass = detection.wasteClass,
                        box = detection.box,
                        lastSeenEpochMs = detection.capturedAtEpochMs,
                        missedFrames = 0,
                        state = TrackState.TRACKED,
                    ),
                )
            } else {
                updated.add(
                    TrackedObject(
                        trackId = "T-${nextTrackNumber++}",
                        wasteClass = detection.wasteClass,
                        box = detection.box,
                        firstSeenEpochMs = detection.capturedAtEpochMs,
                        lastSeenEpochMs = detection.capturedAtEpochMs,
                        missedFrames = 0,
                        state = TrackState.TRACKED,
                    ),
                )
            }
        }

        val released = unmatched.map { track ->
            val missed = track.missedFrames + 1
            track.copy(
                missedFrames = missed,
                state = if (missed >= config.maxMissedFramesBeforeRelease) {
                    TrackState.RELEASED
                } else {
                    TrackState.TRACKED
                },
            )
        }

        tracks = (updated + released).toMutableList()
        return snapshot()
    }

    /** Buang track yang sudah dilepas dari daftar kerja. */
    fun purgeReleased(): TrackerSnapshot {
        tracks = tracks.filter { it.isTracked }.toMutableList()
        return snapshot()
    }
}
