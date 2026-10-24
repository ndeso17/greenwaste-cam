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

    /** Pindahkan [from] menjadi [to]; implementasi berkas memakai rename atomik. */
    fun replace(from: String, to: String)

    fun exists(name: String): Boolean
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
    }
}
