package id.pemilah.ui

/**
 * Keadaan lipat rail navigasi (UI-07). Sengaja bebas Android supaya dapat diuji
 * di JVM tanpa Robolectric.
 *
 * `expanded = false` adalah bawaan sesuai instruksi pengguna 27 Oktober 2026.
 * Operator dapat membuka label melalui tombol rail. Keadaan ini bertahan selama proses
 * aplikasi hidup (`rememberSaveable`); tidak dipersistensi ke disk karena
 * D-UI-02 butir 5 tidak memintanya.
 */
internal data class NavigationRailState(val expanded: Boolean = false) {
    fun toggle(): NavigationRailState = copy(expanded = !expanded)
}
