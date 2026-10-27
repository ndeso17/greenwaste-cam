package id.pemilah.ui

import id.pemilah.R

/**
 * Enam tujuan menu sesuai PRD bagian 9. Label tampilan mengikuti D-UI-02
 * (Operasi, Models, Bot, Settings, Kalibrasi, Logs) dan menjadi **satu-satunya**
 * sumber teks, termasuk untuk deskripsi aksesibilitas.
 *
 * Label PRD induk ("Kamera / Operasi", "Model & Kelas", …) sengaja tidak dipakai
 * sebagai `contentDescription`: nama aksesibilitas wajib memuat teks yang
 * terlihat (WCAG 2.5.3 *Label in Name*), dan pemetaan label PRD → label tampilan
 * sudah dicatat di `Camera/.work/PRD.md` §6.6 serta
 * `Camera/.work/OperationScreen.xml` (`prdLabel`).
 *
 * Urutan mengikuti `navigationRail` pada `.work/OperationScreen.xml`.
 */
enum class AppDestination(val titleRes: Int) {
    OPERATION(R.string.menu_operation),
    MODELS(R.string.menu_models),
    MODEL_TEST(R.string.menu_model_test),
    GALLERY(R.string.menu_gallery),
    BINS(R.string.menu_bins),
    MACHINE(R.string.menu_machine),
    CALIBRATION(R.string.menu_calibration),
    HISTORY(R.string.menu_history),
}
