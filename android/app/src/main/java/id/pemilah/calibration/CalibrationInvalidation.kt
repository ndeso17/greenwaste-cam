package id.pemilah.calibration

import id.pemilah.config.MachineConfig

/**
 * Invalidasi kalibrasi berdasarkan perubahan geometri (CAL-06, invarian 10).
 *
 * Perubahan yang memaksa `needs_check`: resolusi capture, rotasi, area kerja
 * (blok/poligon), dan dimensi conveyor. Aplikasi tidak pernah menaikkan status
 * kembali menjadi `valid`; hanya operator yang dapat melakukannya.
 */
object CalibrationInvalidation {

    data class GeometrySnapshot(
        val captureWidthPx: Int,
        val captureHeightPx: Int,
        val rotationDegrees: Int,
        val conveyorGeometry: List<Double>,
        val blockPolygons: List<List<Double>>,
    ) {
        companion object {
            fun of(calibration: CameraCalibration, config: MachineConfig): GeometrySnapshot =
                GeometrySnapshot(
                    captureWidthPx = calibration.captureWidthPx,
                    captureHeightPx = calibration.captureHeightPx,
                    rotationDegrees = calibration.rotationDegrees,
                    conveyorGeometry = listOfNotNull(
                        config.conveyor.surfaceWidthMm,
                        config.conveyor.turnRadiusMm,
                    ) + config.conveyor.segmentLengthsMm,
                    blockPolygons = listOf(
                        config.conveyor.detectionArea,
                        config.conveyor.pickupArea,
                    ).map { area -> area.flatMap { listOf(it.x, it.y) } },
                )
        }
    }

    fun reasonFor(previous: GeometrySnapshot, current: GeometrySnapshot): ValidityReason? = when {
        previous.captureWidthPx != current.captureWidthPx ||
            previous.captureHeightPx != current.captureHeightPx -> ValidityReason.CAPTURE_RESOLUTION_CHANGED
        previous.rotationDegrees != current.rotationDegrees -> ValidityReason.ROTATION_CHANGED
        previous.conveyorGeometry != current.conveyorGeometry ||
            previous.blockPolygons != current.blockPolygons -> ValidityReason.GEOMETRY_CHANGED
        else -> null
    }

    /**
     * Terapkan penanda pada kalibrasi bila geometri berubah. Status `valid` pada
     * kalibrasi baru hanya dipertahankan bila tidak ada perubahan geometri.
     */
    fun apply(
        calibration: CameraCalibration,
        previous: GeometrySnapshot,
        current: GeometrySnapshot,
    ): CameraCalibration {
        val reason = reasonFor(previous, current) ?: return calibration
        return calibration.copy(validity = CalibrationValidity.needsCheck(reason))
    }
}
