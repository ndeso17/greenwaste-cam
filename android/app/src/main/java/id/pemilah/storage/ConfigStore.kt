package id.pemilah.storage

import id.pemilah.calibration.CameraCalibration
import id.pemilah.config.MachineConfig

/** Hasil simpan; kegagalan dilaporkan, tidak dilempar ke pemanggil UI. */
sealed interface SaveResult {
    data object Saved : SaveResult
    data class Failed(val reason: StoreProblemReason, val detail: String? = null) : SaveResult
}

/** Hasil muat; data rusak dilaporkan dengan alasan spesifik (NFR-03). */
sealed interface LoadResult<out T> {
    data class Loaded<T>(val value: T) : LoadResult<T>
    data object Absent : LoadResult<Nothing>
    data class Corrupted(val problems: List<StoreProblem>) : LoadResult<Nothing>
}

/**
 * Penyimpanan berkas abstrak. Implementasi nyata memakai direktori aplikasi
 * (`FileConfigStorage`); implementasi uji memakai memori.
 */
interface ConfigStorage {
    fun read(name: String): String?
    fun write(name: String, content: String)

    /** Isi berkas biner, atau `null` bila berkas tidak ada. */
    fun readBytes(name: String): ByteArray?

    /** Tulis berkas biner apa adanya (tanpa konversi teks). */
    fun writeBytes(name: String, content: ByteArray)

    /** Pindahkan [from] menjadi [to]; implementasi berkas memakai rename atomik. */
    fun replace(from: String, to: String)

    fun exists(name: String): Boolean

    /**
     * Nama berkas yang tersedia dan berawalan [prefix], urut menaik.
     *
     * Berkas sementara `<nama>.tmp` yang ditinggalkan tulisan yang gagal tidak
     * termasuk, sehingga daftar hanya memuat berkas yang sudah lengkap
     * (MOD-01, NFR-03). Daftar kosong bukan galat: artinya belum ada profil.
     */
    fun listNames(prefix: String): List<String>
}

/**
 * Penyimpanan konfigurasi mesin, kalibrasi, dan berkas profil (WP-11, NFR-03).
 *
 * Aturan yang dijaga:
 * - Konfigurasi disimpan apa adanya lewat [ConfigCodec]; `null` tetap `null`
 *   sehingga tidak ada angka mesin yang lahir dari penyimpanan.
 * - Data rusak **tidak** diganti nilai bawaan: [LoadResult.Corrupted] membawa
 *   kunci yang bermasalah agar operator dapat memperbaikinya.
 * - Simpan atomik: berkas ditulis ke nama sementara lalu dipindahkan, sehingga
 *   mati listrik di tengah tulis tidak meninggalkan konfigurasi separuh.
 * - Berkas kalibrasi dan konfigurasi dipisah agar kalibrasi dapat divalidasi
 *   ulang tanpa menyentuh konfigurasi mesin (CAL-06).
 */
class ConfigStore(private val storage: ConfigStorage) {

    fun loadConfig(): LoadResult<MachineConfig> = load(CONFIG_FILE) { text ->
        when (val result = ConfigCodec.decodeConfig(text)) {
            is CodecResult.Success -> result.value
            is CodecResult.Failure -> throw CodecFailure(result.problems)
        }
    }

    fun saveConfig(config: MachineConfig): SaveResult =
        write(CONFIG_FILE, ConfigCodec.encodeConfig(config))

    fun loadCalibration(): LoadResult<CameraCalibration> = load(CALIBRATION_FILE) { text ->
        when (val result = ConfigCodec.decodeCalibration(text)) {
            is CodecResult.Success -> result.value
            is CodecResult.Failure -> throw CodecFailure(result.problems)
        }
    }

    fun saveCalibration(calibration: CameraCalibration): SaveResult =
        write(CALIBRATION_FILE, ConfigCodec.encodeCalibration(calibration))

    /** Berkas profil disimpan mentah; validasinya milik `ProfileValidator` (WP-14). */
    fun readProfileFile(name: String): String? = runCatching { storage.read(name) }.getOrNull()

    fun writeProfileFile(name: String, content: String): SaveResult = write(name, content)

    /**
     * Nama berkas profil yang sudah lengkap, urut menaik (MOD-01).
     *
     * Berkas sementara `.tmp` tidak termasuk karena tulisan yang gagal di tengah
     * jalan belum tentu utuh. Daftar kosong berarti belum ada profil, bukan galat.
     */
    fun listProfileFiles(): List<String> = runCatching { storage.listNames(PROFILE_PREFIX) }
        .getOrDefault(emptyList())
        .filter { it.endsWith(PROFILE_EXTENSION) }

    fun profileFileExists(name: String): Boolean =
        runCatching { storage.exists(name) }.getOrDefault(false)

    /**
     * Isi berkas model ONNX tersimpan (D-09, MOD-01).
     *
     * `null` berarti berkas tidak ada atau tidak terbaca; pemanggil melaporkan
     * keadaan itu sebagai profil tanpa berkas model, bukan mengarang model.
     */
    fun readModelFile(name: String): ByteArray? = runCatching { storage.readBytes(name) }
        .getOrNull()

    /**
     * Salin berkas model ke penyimpanan aplikasi lewat jalur tulis atomik.
     *
     * Berkas ditulis ke nama sementara lalu dipindahkan, sehingga mati listrik di
     * tengah tulis tidak meninggalkan berkas model separuh yang tampak sah.
     */
    fun writeModelFile(name: String, content: ByteArray): SaveResult {
        val temporary = "$name.tmp"
        return try {
            storage.writeBytes(temporary, content)
            storage.replace(temporary, name)
            SaveResult.Saved
        } catch (error: Exception) {
            SaveResult.Failed(
                StoreProblemReason.WRITE_FAILED,
                error.message ?: error::class.java.simpleName,
            )
        }
    }

    fun modelFileExists(name: String): Boolean =
        runCatching { storage.exists(name) }.getOrDefault(false)

    /**
     * `profileId` profil aktif; `null` berarti belum pernah dipilih (MOD-03).
     *
     * Berkas kosong atau hanya berisi spasi juga dianggap belum dipilih supaya
     * tulisan yang terpotong tidak menjadi nama profil hantu.
     */
    fun readActiveProfileId(): String? = runCatching { storage.read(ACTIVE_PROFILE_FILE) }
        .getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    fun writeActiveProfileId(profileId: String): SaveResult =
        write(ACTIVE_PROFILE_FILE, profileId)

    private fun <T> load(name: String, decode: (String) -> T): LoadResult<T> {
        val text = runCatching { storage.read(name) }.getOrElse {
            return LoadResult.Corrupted(listOf(StoreProblem(name, StoreProblemReason.CORRUPTED)))
        } ?: return LoadResult.Absent
        return try {
            LoadResult.Loaded(decode(text))
        } catch (failure: CodecFailure) {
            LoadResult.Corrupted(failure.problems)
        } catch (error: Exception) {
            LoadResult.Corrupted(listOf(StoreProblem(name, StoreProblemReason.CORRUPTED)))
        }
    }

    private fun write(name: String, content: String): SaveResult {
        val temporary = "$name.tmp"
        return try {
            storage.write(temporary, content)
            storage.replace(temporary, name)
            SaveResult.Saved
        } catch (error: Exception) {
            SaveResult.Failed(
                StoreProblemReason.WRITE_FAILED,
                error.message ?: error::class.java.simpleName,
            )
        }
    }

    private class CodecFailure(val problems: List<StoreProblem>) : Exception()

    companion object {
        const val CONFIG_FILE: String = "machine-config.txt"
        const val CALIBRATION_FILE: String = "camera-calibration.txt"

        /** Awalan nama berkas profil; dipakai juga oleh `ProfileRepository`. */
        const val PROFILE_PREFIX: String = "profile-"
        const val PROFILE_EXTENSION: String = ".json"

        /** Awalan dan akhiran berkas model ONNX tersimpan (D-09, MOD-01). */
        const val MODEL_PREFIX: String = "model-"
        const val MODEL_EXTENSION: String = ".onnx"

        /** Nama berkas penanda profil aktif (MOD-03). */
        const val ACTIVE_PROFILE_FILE: String = "active-profile.txt"
    }
}
