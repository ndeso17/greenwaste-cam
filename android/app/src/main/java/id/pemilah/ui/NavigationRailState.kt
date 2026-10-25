package id.pemilah.ui

/**
 * Keadaan lipat rail navigasi (UI-07). Sengaja bebas Android supaya dapat diuji
 * di JVM tanpa Robolectric.
 *
 * `expanded = true` adalah bawaan: operator baru melihat label lengkap lebih
 * dulu, lalu boleh memperkecil. Keadaan ini hanya bertahan selama proses
 * aplikasi hidup (`rememberSaveable`); tidak dipersistensi ke disk karena
 * D-UI-02 butir 5 tidak memintanya.
 */
internal data class NavigationRailState(val expanded: Boolean = true) {
    fun toggle(): NavigationRailState = copy(expanded = !expanded)
}
