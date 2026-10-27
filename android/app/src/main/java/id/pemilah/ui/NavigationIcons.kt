package id.pemilah.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.ModelTraining
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Ikon navigasi dari pustaka ikon Material (D-05). Pemetaan berada di satu
 * berkas supaya penggantian pustaka hanya menyentuh berkas ini, dan supaya nama
 * ikon yang salah ketik gagal saat kompilasi, bukan saat aplikasi berjalan.
 *
 * Gaya `Icons.Rounded` dipakai seragam agar rail terlihat satu keluarga.
 * `Icons.Rounded.Settings` berasal dari `material-icons-core` (transitif lewat
 * Material3); sisanya dari `material-icons-extended`.
 */
internal object NavigationIcons {
    /** Ikon untuk membuka rail: chevron menunjuk ke luar, arah membuka. */
    val expand: ImageVector = Icons.Rounded.ChevronRight

    /** Ikon untuk menutup rail: chevron menunjuk ke dalam, arah menutup. */
    val collapse: ImageVector = Icons.Rounded.ChevronLeft
}

/**
 * Ikon rail untuk setiap tujuan. Nama ikon mengikuti label tampilan D-UI-02,
 * bukan label PRD induk, karena yang terlihat operator adalah label baru:
 * "Bot" tetap berarti Bak dan Robot, "Logs" tetap berarti Status & Riwayat.
 */
internal fun AppDestination.icon(): ImageVector = when (this) {
    AppDestination.OPERATION -> Icons.Rounded.PhotoCamera
    AppDestination.MODELS -> Icons.Rounded.ModelTraining
    AppDestination.MODEL_TEST -> Icons.Rounded.Science
    AppDestination.GALLERY -> Icons.Rounded.Collections
    AppDestination.BINS -> Icons.Rounded.SmartToy
    AppDestination.MACHINE -> Icons.Rounded.Settings
    AppDestination.CALIBRATION -> Icons.Rounded.CenterFocusStrong
    AppDestination.HISTORY -> Icons.Rounded.ReceiptLong
}
