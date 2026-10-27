package id.pemilah.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG AA requirement evaluated against the worst possible sRGB camera background. */
class CameraOverlaySurfaceTest {
    @Test
    fun whiteText_overBrightestCamera_meetsWcagAa() {
        val brightestCameraChannel = 1.0
        val composedBackground = brightestCameraChannel * (1.0 - CameraOverlayScrimAlpha)
        val contrast = 1.05 / (linear(composedBackground) + 0.05)
        assertTrue("White overlay text contrast $contrast is below WCAG AA", contrast >= 4.5)
    }

    @Test
    fun whiteText_overCameraPrimaryColors_meetsWcagAa() {
        val channelLevels = listOf(0.0, 0.5, 1.0)
        for (red in channelLevels) for (green in channelLevels) for (blue in channelLevels) {
            val alpha = 1.0 - CameraOverlayScrimAlpha
            val luminance = 0.2126 * linear(red * alpha) +
                0.7152 * linear(green * alpha) + 0.0722 * linear(blue * alpha)
            assertTrue(1.05 / (luminance + 0.05) >= 4.5)
        }
    }

    private fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
}
