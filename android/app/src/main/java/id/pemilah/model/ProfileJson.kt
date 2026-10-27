package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Codec JSON berkas profil model sesuai `.work/Schema.md` §3 (MOD-01, MOD-02).
 *
 * Aturan yang dijaga:
 * - Nama kunci JSON mengikat pada `Schema.md` §3 dan tidak boleh diubah di sini.
 * - Decode bersifat ketat: kunci asing ditolak dan field wajib tidak memiliki
 *   nilai bawaan, sehingga profil cacat tidak lolos karena nilai implisit
 *   (`Rules.md` §2.2, `NFR-03`).
 * - Kegagalan dikembalikan sebagai nilai bertipe, bukan pengecualian ke UI
 *   (`Styleguide.md` §3).
 * - Enum di JSON memakai bentuk `snake_case` huruf kecil (`scale_0_1`,
 *   `yolo_v8`) dan kelas resmi memakai bentuk huruf besar enum Kotlin
 *   (`ORGANIK`), persis seperti contoh pada `Schema.md` §3.
 *
 * Codec ini **tidak** memvalidasi kelayakan profil; itu tugas [ProfileValidator]
 * (MOD-02). Codec hanya memastikan berkas dapat dibaca menjadi [ModelProfile].
 */
object ProfileJson {

    /** Versi skema yang dikenali; nilai lain ditolak dengan alasan spesifik. */
    const val SUPPORTED_SCHEMA_VERSION: Int = 1

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    private val normalizationNames: Map<Normalization, String> = mapOf(
        Normalization.SCALE_0_1 to "scale_0_1",
        Normalization.SCALE_MINUS_ONE_ONE to "scale_minus_one_one",
        Normalization.NONE to "none",
    )

    private val layoutNames: Map<TensorLayout, String> = mapOf(
        TensorLayout.NCHW to "nchw",
        TensorLayout.NHWC to "nhwc",
    )

    private val outputFormatNames: Map<OutputFormat, String> = mapOf(
        OutputFormat.YOLO_V8 to "yolo_v8",
        OutputFormat.YOLO_V5 to "yolo_v5",
        OutputFormat.SSD_BOXES to "ssd_boxes",
        OutputFormat.CLASSIFICATION_ONLY to "classification_only",
    )

    // `MissingFieldException.missingFields` masih bertanda eksperimental pada
    // kotlinx-serialization 1.7.3, tetapi justru itu yang memberi nama field
    // wajib yang hilang alih-alih pesan bebas. Opt-in dinyatakan di sini, bukan
    // di seluruh modul.
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(text: String): ProfileDecodeResult {
        val dto = try {
            json.decodeFromString(ProfileDto.serializer(), text)
        } catch (missing: MissingFieldException) {
            return ProfileDecodeResult.Malformed(
                missing.missingFields.map { field ->
                    ProfileJsonProblem(field, ProfileJsonProblemReason.MISSING_FIELD)
                }.ifEmpty {
                    listOf(ProfileJsonProblem("", ProfileJsonProblemReason.INVALID_JSON))
                },
            )
        } catch (invalid: SerializationException) {
            return ProfileDecodeResult.Malformed(
                listOf(
                    ProfileJsonProblem(
                        field = "",
                        reason = ProfileJsonProblemReason.INVALID_JSON,
                        detail = invalid.message,
                    ),
                ),
            )
        } catch (invalid: IllegalArgumentException) {
            return ProfileDecodeResult.Malformed(
                listOf(
                    ProfileJsonProblem(
                        field = "",
                        reason = ProfileJsonProblemReason.INVALID_JSON,
                        detail = invalid.message,
                    ),
                ),
            )
        }

        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            return ProfileDecodeResult.Malformed(
                listOf(
                    ProfileJsonProblem(
                        field = "schemaVersion",
                        reason = ProfileJsonProblemReason.UNKNOWN_SCHEMA_VERSION,
                        detail = dto.schemaVersion.toString(),                    ),
                ),
            )
        }

        val problems = mutableListOf<ProfileJsonProblem>()
        val normalization = decodeEnum(
            raw = dto.input.normalization,
            names = normalizationNames,
            field = "input.normalization",
            problems = problems,
        )
        val layout = decodeEnum(
            raw = dto.input.layout,
            names = layoutNames,
            field = "input.layout",
            problems = problems,
        )
        val outputFormat = decodeEnum(
            raw = dto.output.format,
            names = outputFormatNames,
            field = "output.format",
            problems = problems,
        )
        val classMapping = decodeClassMapping(dto.classMapping, problems)
        val binAssignment = decodeBinAssignment(dto.binAssignment, problems)

        if (problems.isNotEmpty()) return ProfileDecodeResult.Malformed(problems)

        return ProfileDecodeResult.Decoded(
            ModelProfile(
                schemaVersion = dto.schemaVersion,
                profileId = dto.profileId,
                name = dto.name,
                version = dto.version,
                modelFile = dto.modelFile,
                labels = dto.labels,
                classMapping = classMapping,
                input = InputSpec(
                    width = dto.input.width,
                    height = dto.input.height,
                    normalization = requireNotNull(normalization) {
                        "normalization sudah diperiksa sebelumnya"
                    },
                    layout = requireNotNull(layout) { "layout sudah diperiksa sebelumnya" },
                ),
                output = OutputSpec(
                    format = requireNotNull(outputFormat) { "output.format sudah diperiksa" },
                    boxesNormalized = dto.output.boxesNormalized,
                    hasObjectness = dto.output.hasObjectness,
                ),
                confidenceThreshold = dto.confidenceThreshold,
                groupingEnabled = dto.groupingEnabled,
                binAssignment = binAssignment,
            ),
        )
    }

    fun encode(profile: ModelProfile): String = json.encodeToString(
        ProfileDto.serializer(),
        ProfileDto(
            schemaVersion = profile.schemaVersion,
            profileId = profile.profileId,
            name = profile.name,
            version = profile.version,
            modelFile = profile.modelFile,
            labels = profile.labels,
            classMapping = profile.classMapping.entries.associate { (label, wasteClass) ->
                label to wasteClass.name
            },
            input = InputDto(
                width = profile.input.width,
                height = profile.input.height,
                normalization = normalizationNames.getValue(profile.input.normalization),
                layout = layoutNames.getValue(profile.input.layout),
            ),
            output = OutputDto(
                format = outputFormatNames.getValue(profile.output.format),
                boxesNormalized = profile.output.boxesNormalized,
                hasObjectness = profile.output.hasObjectness,
            ),
            confidenceThreshold = profile.confidenceThreshold,
            groupingEnabled = profile.groupingEnabled,
            binAssignment = profile.binAssignment.entries.associate { (wasteClass, bin) ->
                wasteClass.name to bin
            },
        ),
    )

    /**
     * Nama kunci JSON untuk satu `WasteClass`. Dipakai pengujian dan pesan galat
     * supaya berkas profil menyebut kelas dengan ejaan yang sama seperti
     * `Schema.md` §3.
     */
    fun wasteClassName(wasteClass: WasteClass): String = wasteClass.name

    private fun <T> decodeEnum(
        raw: String,
        names: Map<T, String>,
        field: String,
        problems: MutableList<ProfileJsonProblem>,
    ): T? {
        val match = names.entries.firstOrNull { it.value == raw }?.key
        if (match == null) {
            problems.add(
                ProfileJsonProblem(field, ProfileJsonProblemReason.INVALID_VALUE, raw),
            )
        }
        return match
    }

    private fun decodeClassMapping(
        raw: Map<String, String>,
        problems: MutableList<ProfileJsonProblem>,
    ): Map<String, WasteClass> {
        val result = mutableMapOf<String, WasteClass>()
        raw.forEach { (label, className) ->
            val wasteClass = WasteClass.entries.firstOrNull { it.name == className }
            if (wasteClass == null) {
                problems.add(
                    ProfileJsonProblem(
                        field = "classMapping.$label",
                        reason = ProfileJsonProblemReason.UNKNOWN_WASTE_CLASS,
                        detail = className,
                    ),
                )
            } else {
                result[label] = wasteClass
            }
        }
        return result
    }

    private fun decodeBinAssignment(
        raw: Map<String, String?>,
        problems: MutableList<ProfileJsonProblem>,
    ): Map<WasteClass, String?> {
        val result = mutableMapOf<WasteClass, String?>()
        raw.forEach { (className, bin) ->
            val wasteClass = WasteClass.entries.firstOrNull { it.name == className }
            if (wasteClass == null) {
                problems.add(
                    ProfileJsonProblem(
                        field = "binAssignment.$className",
                        reason = ProfileJsonProblemReason.UNKNOWN_WASTE_CLASS,
                        detail = className,
                    ),
                )
            } else {
                result[wasteClass] = bin
            }
        }
        return result
    }
}

/** Hasil decode berkas profil; kegagalan membawa alasan yang dapat ditampilkan. */
sealed interface ProfileDecodeResult {
    data class Decoded(val profile: ModelProfile) : ProfileDecodeResult
    data class Malformed(val problems: List<ProfileJsonProblem>) : ProfileDecodeResult
}

data class ProfileJsonProblem(
    val field: String,
    val reason: ProfileJsonProblemReason,
    val detail: String? = null,
)

enum class ProfileJsonProblemReason {
    INVALID_JSON,
    MISSING_FIELD,
    UNKNOWN_SCHEMA_VERSION,
    INVALID_VALUE,
    UNKNOWN_WASTE_CLASS,
}

@Serializable
private data class ProfileDto(
    val schemaVersion: Int,
    val profileId: String,
    val name: String,
    val version: String,
    val modelFile: String,
    val labels: List<String>,
    val classMapping: Map<String, String>,
    val input: InputDto,
    val output: OutputDto,
    val confidenceThreshold: Double,
    val groupingEnabled: Boolean,
    val binAssignment: Map<String, String?>,
)

@Serializable
private data class InputDto(
    val width: Int,
    val height: Int,
    val normalization: String,
    val layout: String,
)

@Serializable
private data class OutputDto(
    val format: String,
    val boxesNormalized: Boolean,
    val hasObjectness: Boolean,
)
