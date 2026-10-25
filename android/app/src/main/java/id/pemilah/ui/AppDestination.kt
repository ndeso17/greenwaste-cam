package id.pemilah.ui

import id.pemilah.R

/**
 * Enam tujuan menu sesuai PRD bagian 9. Label tampilan mengikuti D-UI-02
 * (Operasi, Models, Bot, Settings, Kalibrasi, Logs); [prdTitleRes] menyimpan
 * label PRD induk untuk deskripsi aksesibilitas supaya singkatan tidak
 * menghilangkan makna requirement aslinya. Urutan mengikuti navigationRail pada
 * `.work/OperationScreen.xml`.
 */
enum class AppDestination(val titleRes: Int, val prdTitleRes: Int) {
    OPERATION(R.string.menu_operation, R.string.menu_operation_prd),
    MODELS(R.string.menu_models, R.string.menu_models_prd),
    BINS(R.string.menu_bins, R.string.menu_bins_prd),
    MACHINE(R.string.menu_machine, R.string.menu_machine_prd),
    CALIBRATION(R.string.menu_calibration, R.string.menu_calibration_prd),
    HISTORY(R.string.menu_history, R.string.menu_history_prd),
}
