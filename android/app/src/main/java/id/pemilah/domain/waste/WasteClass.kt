package id.pemilah.domain.waste

/**
 * Tujuh kelas resmi sesuai PRD induk bagian 5 dan `.work/Schema.md` §2.
 *
 * Enum ini tidak boleh memuat `KERTAS`, `LOGAM`, atau `UNKNOWN`: objek tak dikenal
 * diwakili `null`, dan label gabungan Kaleng+Besi wajib ditolak oleh validator
 * profil (MOD-02), bukan ditampung di sini.
 */
enum class WasteClass {
    ORGANIK,
    PLASTIK,
    KALENG,
    STYROFOAM,
    KACA,
    KARDUS,
    BESI,
}
