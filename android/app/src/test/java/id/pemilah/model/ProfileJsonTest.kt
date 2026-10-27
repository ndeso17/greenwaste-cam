package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bukti codec JSON profil (WP-02): putaran penuh, field wajib hilang, versi
 * skema tak dikenal, nilai enum tak dikenal, kelas di luar tujuh kelas resmi,
 * dan penolakan label gabungan `Kaleng`+`Besi` oleh [ProfileValidator].
 */
class ProfileJsonTest {

    private val validProfileJson = """
        {
          "schemaVersion": 1,
          "profileId": "6f1a1d2c-0000-4000-8000-000000000001",
          "name": "yolov8-sampah-v2",
          "version": "2.0.0",
          "modelFile": "profile-assets/yolov8-sampah-v2.onnx",
          "labels": ["Organik", "Kertas_lembaran"],
          "classMapping": { "Organik": "ORGANIK", "Kertas_lembaran": "KARDUS" },
          "input": {
            "width": 640, "height": 640,
            "normalization": "scale_0_1", "layout": "nchw"
          },
          "output": {
            "format": "yolo_v8", "boxesNormalized": true, "hasObjectness": false
          },
          "confidenceThreshold": 0.45,
          "groupingEnabled": false,
          "binAssignment": { "ORGANIK": "B1", "KARDUS": null }
        }
    """.trimIndent()

    private fun decodeValid(): ModelProfile {
        val result = ProfileJson.decode(validProfileJson)
        assertTrue(
            "profil contoh harus dapat dibaca: $result",
            result is ProfileDecodeResult.Decoded,
        )
        return (result as ProfileDecodeResult.Decoded).profile
    }

    @Test
    fun decode_thenEncode_reproducesEquivalentProfile() {
        val original = decodeValid()
        val roundTrip = ProfileJson.decode(ProfileJson.encode(original))
        assertTrue(roundTrip is ProfileDecodeResult.Decoded)
        assertEquals(original, (roundTrip as ProfileDecodeResult.Decoded).profile)
    }

    @Test
    fun encode_mapsEnumsToSchemaSpellings() {
        val text = ProfileJson.encode(decodeValid())
        assertTrue(text.contains("\"scale_0_1\""))
        assertTrue(text.contains("\"nchw\""))
        assertTrue(text.contains("\"yolo_v8\""))
        assertTrue(text.contains("\"ORGANIK\""))
    }

    @Test
    fun decode_missingRequiredField_namesMissingField() {
        val withoutName = validProfileJson.replace("\"name\": \"yolov8-sampah-v2\",", "")
        val result = ProfileJson.decode(withoutName)
        assertTrue(result is ProfileDecodeResult.Malformed)
        val problems = (result as ProfileDecodeResult.Malformed).problems
        assertTrue(
            "field `name` harus disebut: $problems",
            problems.any {
                it.field == "name" && it.reason == ProfileJsonProblemReason.MISSING_FIELD
            },
        )
    }

    @Test
    fun decode_unknownSchemaVersion_rejectedWithReason() {
        val bumped = validProfileJson.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        val result = ProfileJson.decode(bumped)
        assertTrue(result is ProfileDecodeResult.Malformed)
        val problems = (result as ProfileDecodeResult.Malformed).problems
        assertEquals(
            listOf(
                ProfileJsonProblem(
                    "schemaVersion",
                    ProfileJsonProblemReason.UNKNOWN_SCHEMA_VERSION,
                    "99",
                ),
            ),
            problems,
        )
    }

    @Test
    fun decode_unknownNormalization_rejectedNamingNestedField() {
        val altered = validProfileJson.replace("\"scale_0_1\"", "\"scale_bytes\"")
        val result = ProfileJson.decode(altered)
        assertTrue(result is ProfileDecodeResult.Malformed)
        val problems = (result as ProfileDecodeResult.Malformed).problems
        assertTrue(
            "field bersarang harus disebut: $problems",
            problems.any {
                it.field == "input.normalization" &&
                    it.reason == ProfileJsonProblemReason.INVALID_VALUE
            },
        )
    }

    @Test
    fun decode_classOutsideSevenOfficialClasses_rejected() {
        val altered = validProfileJson.replace(
            "\"classMapping\": { \"Organik\": \"ORGANIK\", \"Kertas_lembaran\": \"KARDUS\" }",
            "\"classMapping\": { \"Organik\": \"ORGANIK\", \"Kertas_lembaran\": \"KERTAS\" }",
        )
        val result = ProfileJson.decode(altered)
        assertTrue(result is ProfileDecodeResult.Malformed)
        val problems = (result as ProfileDecodeResult.Malformed).problems
        assertTrue(
            "kelas di luar tujuh kelas resmi harus ditolak: $problems",
            problems.any {
                it.field == "classMapping.Kertas_lembaran" &&
                    it.reason == ProfileJsonProblemReason.UNKNOWN_WASTE_CLASS
            },
        )
    }

    @Test
    fun decode_unknownTopLevelKey_rejectedAsInvalidJson() {
        val altered = validProfileJson.replace(
            "\"groupingEnabled\": false,",
            "\"groupingEnabled\": false, \"extraKey\": 1,",
        )
        val result = ProfileJson.decode(altered)
        assertTrue(
            "kunci asing harus ditolak oleh decode ketat",
            result is ProfileDecodeResult.Malformed,
        )
        assertEquals(
            ProfileJsonProblemReason.INVALID_JSON,
            (result as ProfileDecodeResult.Malformed).problems.first().reason,
        )
    }

    @Test
    fun decode_notJson_rejectedAsInvalidJson() {
        val result = ProfileJson.decode("bukan json")
        assertTrue(result is ProfileDecodeResult.Malformed)
        assertEquals(
            ProfileJsonProblemReason.INVALID_JSON,
            (result as ProfileDecodeResult.Malformed).problems.first().reason,
        )
    }

    @Test
    fun decode_combinedKalengBesiLabel_passedToValidatorAndRejected() {
        val combined = validProfileJson.replace(
            "\"labels\": [\"Organik\", \"Kertas_lembaran\"]",
            "\"labels\": [\"Organik\", \"Logam_metal\"]",
        ).replace(
            "\"classMapping\": { \"Organik\": \"ORGANIK\", \"Kertas_lembaran\": \"KARDUS\" }",
            "\"classMapping\": { \"Organik\": \"ORGANIK\", \"Logam_metal\": \"KALENG\" }",
        )
        val result = ProfileJson.decode(combined)
        assertTrue(
            "codec hanya membaca; kelayakan diperiksa validator",
            result is ProfileDecodeResult.Decoded,
        )
        val validation = ProfileValidator.validate((result as ProfileDecodeResult.Decoded).profile)
        assertTrue(validation is ProfileValidation.Rejected)
        assertTrue(
            "label gabungan Kaleng+Besi harus ditolak (Rules.md §2.6)",
            (validation as ProfileValidation.Rejected).problems.any {
                it.reason == ProfileProblemReason.COMBINED_KALENG_BESI_LABEL
            },
        )
    }

    @Test
    fun decode_validProfile_acceptedByValidator() {
        val validation = ProfileValidator.validate(decodeValid())
        assertEquals(ProfileValidation.Valid, validation)
    }

    @Test
    fun wasteClassName_matchesSchemaSpelling() {
        assertEquals("KALENG", ProfileJson.wasteClassName(WasteClass.KALENG))
        assertEquals("STYROFOAM", ProfileJson.wasteClassName(WasteClass.STYROFOAM))
    }
}
