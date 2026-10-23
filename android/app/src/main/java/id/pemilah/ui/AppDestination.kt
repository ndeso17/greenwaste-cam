package id.pemilah.ui

import id.pemilah.R

/**
 * Enam tujuan menu sesuai PRD bagian 9. Urutan dan label mengikuti
 * [.work/OperationScreen.xml] navigationRail; label berasal dari R.string (D-03).
 */
enum class AppDestination(val titleRes: Int) {
    OPERATION(R.string.menu_operation),
    MODELS(R.string.menu_models),
    BINS(R.string.menu_bins),
    MACHINE(R.string.menu_machine),
    CALIBRATION(R.string.menu_calibration),
    HISTORY(R.string.menu_history),
}
