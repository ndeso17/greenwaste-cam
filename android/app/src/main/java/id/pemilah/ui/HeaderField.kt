package id.pemilah.ui

import id.pemilah.R

/**
 * Label kolom `statusHeader` pada `.work/OperationScreen.xml`.
 *
 * Nilai teks berasal dari `R.string` (D-03); enum ini hanya pemetaan status →
 * sumber teks dan warna, sehingga Composable tidak memuat keputusan status.
 */
enum class HeaderField(val labelRes: Int) {
    PROFILE(R.string.header_profile),
    CAMERA(R.string.header_camera),
    CALIBRATION(R.string.header_calibration),
    USB(R.string.header_usb),
}
