package id.pemilah.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontrak rail navigasi (UI-07) yang tidak bergantung pada Android:
 * keadaan lipat, lebar minimum, dan kelengkapan tujuan.
 *
 * Yang diuji adalah kontrak yang ditulis di D-UI-02 dan Design.md §4.4, bukan
 * salinan implementasi.
 */
class NavigationRailStateTest {

    // --- Keadaan lipat -----------------------------------------------------

    @Test
    fun `rail terbuka secara bawaan`() {
        assertTrue(NavigationRailState().expanded)
    }

    @Test
    fun `satu toggle menutup rail`() {
        val tertutup = NavigationRailState().toggle()

        assertFalse(tertutup.expanded)
    }

    @Test
    fun `dua toggle membuka rail kembali`() {
        val kembali = NavigationRailState().toggle().toggle()

        assertTrue(kembali.expanded)
    }

    @Test
    fun `toggle bersifat bolak-balik dan tidak mengubah keadaan lain`() {
        val awal = NavigationRailState(expanded = false)

        assertEquals(NavigationRailState(expanded = true), awal.toggle())
        assertEquals(awal, awal.toggle().toggle())
    }

    // --- Lebar rail --------------------------------------------------------

    @Test
    fun `lebar rail tertutup memenuhi target sentuh minimum`() {
        // Design.md §7: target sentuh minimal 48 dp.
        assertTrue(NavigationRailWidths.collapsed.value >= 48f)
    }

    @Test
    fun `lebar rail tertutup adalah 72 dp sesuai D-UI-02`() {
        assertEquals(72f, NavigationRailWidths.collapsed.value, 0.01f)
    }

    @Test
    fun `lebar rail terbuka lebih besar daripada lebar tertutup`() {
        assertTrue(NavigationRailWidths.expanded.value > NavigationRailWidths.collapsed.value)
    }

    @Test
    fun `lebar rail terbuka adalah 232 dp sesuai D-UI-02`() {
        assertEquals(232f, NavigationRailWidths.expanded.value, 0.01f)
    }

    // --- Kelengkapan tujuan ------------------------------------------------

    @Test
    fun `terdapat tepat enam tujuan navigasi`() {
        assertEquals(6, AppDestination.entries.size)
    }

    @Test
    fun `urutan tujuan mengikuti navigationRail pada OperationScreen xml`() {
        assertEquals(
            listOf(
                AppDestination.OPERATION,
                AppDestination.MODELS,
                AppDestination.BINS,
                AppDestination.MACHINE,
                AppDestination.CALIBRATION,
                AppDestination.HISTORY,
            ),
            AppDestination.entries.toList(),
        )
    }

    @Test
    fun `setiap tujuan memiliki label tampilan yang berbeda`() {
        val labels = AppDestination.entries.map { it.titleRes }

        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `setiap tujuan memiliki label PRD yang berbeda untuk aksesibilitas`() {
        val labels = AppDestination.entries.map { it.prdTitleRes }

        assertEquals(labels.size, labels.distinct().size)
    }

    @Test
    fun `setiap tujuan memakai kunci string terpisah untuk label tampilan dan label PRD`() {
        // Label ringkas D-UI-02 berbeda teks dari label PRD induk pada lima tujuan;
        // Kalibrasi teksnya sama tetapi tetap punya kunci sendiri, sehingga
        // penggantian salah satu label tidak pernah mengubah yang lain.
        val samaKunci = AppDestination.entries.filter { it.titleRes == it.prdTitleRes }

        assertTrue(
            "kunci label tampilan dan label PRD tidak boleh berbagi ID yang sama",
            samaKunci.isEmpty(),
        )
    }

    @Test
    fun `setiap tujuan memakai kunci string yang sah bukan nol`() {
        val kosong = AppDestination.entries.filter { it.titleRes == 0 || it.prdTitleRes == 0 }

        assertTrue("ada tujuan tanpa kunci string: $kosong", kosong.isEmpty())
    }
}
