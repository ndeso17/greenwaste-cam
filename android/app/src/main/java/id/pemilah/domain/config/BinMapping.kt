package id.pemilah.domain.config

import id.pemilah.config.RobotId
import id.pemilah.domain.waste.WasteClass

/**
 * Identitas bak 1–6. Dibuat hanya lewat [of] supaya nilai di luar B1–B6 tidak
 * pernah menjadi tujuan yang sah.
 */
data class BinId private constructor(val value: String) {

    override fun toString(): String = value

    companion object {
        private val PATTERN = Regex("B[1-6]")

        val B1 = BinId("B1")
        val B2 = BinId("B2")
        val B3 = BinId("B3")
        val B4 = BinId("B4")
        val B5 = BinId("B5")
        val B6 = BinId("B6")
        val ALL: List<BinId> = listOf(B1, B2, B3, B4, B5, B6)

        fun of(value: String?): BinId? =
            if (value != null && PATTERN.matches(value)) BinId(value) else null

        /** Pemilik bak menurut PRD induk bagian 4: B1–B3 kiri, B4–B6 kanan. */
        fun ownerOf(bin: BinId): RobotId? =
            RobotId.entries.firstOrNull { bin.value in it.binIds }
    }
}

data class BinDestination(val bin: BinId, val robot: RobotId)

/** Alasan sebuah kelas tidak memperoleh tujuan bak. */
enum class BinBlockReason {
    /** Belum ada bak yang ditetapkan untuk kelas ini (nilai `null`). */
    NO_BIN_ASSIGNED,

    /** Nilai bak tidak dikenal (bukan B1–B6). */
    UNKNOWN_BIN,

    /** Bak milik robot yang belum diaktifkan. */
    ROBOT_DISABLED,
}

sealed interface BinResolution {
    data class Assigned(val destination: BinDestination) : BinResolution
    data class Blocked(val reason: BinBlockReason) : BinResolution
}

data class MappingProblem(val wasteClass: WasteClass, val reason: BinBlockReason)

/**
 * Pemetaan kelas aktif ke bak tujuan (MOD-05, ARM-06, invarian 9).
 *
 * Hanya menyimpan dan memvalidasi pemetaan; tidak menentukan jangkauan mekanik
 * dan tidak menjadi nilai bawaan. Nilai alokasi berasal dari konfigurasi
 * operator (B-16 terbuka), sehingga `null` berarti belum ada tujuan.
 */
class BinMapping(
    private val assignments: Map<WasteClass, String?>,
    private val enabledRobots: Set<RobotId>,
) {

    fun resolutionFor(wasteClass: WasteClass): BinResolution {
        val bin = BinId.of(assignments[wasteClass])
            ?: return BinResolution.Blocked(
                if (assignments[wasteClass] == null) {
                    BinBlockReason.NO_BIN_ASSIGNED
                } else {
                    BinBlockReason.UNKNOWN_BIN
                },
            )
        val robot = BinId.ownerOf(bin)
            ?: return BinResolution.Blocked(BinBlockReason.UNKNOWN_BIN)
        if (robot !in enabledRobots) {
            return BinResolution.Blocked(BinBlockReason.ROBOT_DISABLED)
        }
        return BinResolution.Assigned(BinDestination(bin, robot))
    }

    /** Kelas aktif yang belum punya tujuan bak valid; kosong berarti lengkap. */
    fun blockersFor(activeClasses: Set<WasteClass>): List<WasteClass> =
        activeClasses.filter { resolutionFor(it) is BinResolution.Blocked }.sorted()

    /** Seluruh masalah pemetaan, terlepas dari kelas mana yang aktif. */
    fun problems(): List<MappingProblem> =
        WasteClass.entries.mapNotNull { wasteClass ->
            when (val resolution = resolutionFor(wasteClass)) {
                is BinResolution.Assigned -> null
                is BinResolution.Blocked -> MappingProblem(wasteClass, resolution.reason)
            }
        }
}
