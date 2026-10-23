package id.pemilah.model

import id.pemilah.domain.waste.WasteClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private object ProfileFixture {
    const val SCHEMA_VERSION = 1
    const val INPUT_SIZE = 640
    const val CONFIDENCE_THRESHOLD = 0.4

    val labels = listOf(
        "Organik",
        "Plastik",
        "Kaleng",
        "Styrofoam",
        "Kaca",
        "Kardus",
        "Besi",
    )

    val mapping = mapOf(
        "Organik" to WasteClass.ORGANIK,
        "Plastik" to WasteClass.PLASTIK,
        "Kaleng" to WasteClass.KALENG,
        "Styrofoam" to WasteClass.STYROFOAM,
        "Kaca" to WasteClass.KACA,
        "Kardus" to WasteClass.KARDUS,
        "Besi" to WasteClass.BESI,
    )

    fun profile(
        labels: List<String> = this.labels,
        mapping: Map<String, WasteClass> = this.mapping,
        output: OutputSpec = OutputSpec(
            format = OutputFormat.YOLO_V8,
            boxesNormalized = true,
            hasObjectness = false,
        ),
        confidenceThreshold: Double = CONFIDENCE_THRESHOLD,
        inputSize: Int = INPUT_SIZE,
    ) = ModelProfile(
        schemaVersion = SCHEMA_VERSION,
        profileId = "profile-1",
        name = "yolov8-sampah",
        version = "1.0.0",
        modelFile = "models/model.onnx",
        labels = labels,
        classMapping = mapping,
        input = InputSpec(
            width = inputSize,
            height = inputSize,
            normalization = Normalization.SCALE_0_1,
            layout = TensorLayout.NCHW,
        ),
        output = output,
        confidenceThreshold = confidenceThreshold,
        groupingEnabled = false,
        binAssignment = emptyMap(),
    )
}

class ProfileValidatorTest {

    private fun reasonsOf(result: ProfileValidation): List<ProfileProblemReason> {
        assertTrue("expected Rejected but was $result", result is ProfileValidation.Rejected)
        return (result as ProfileValidation.Rejected).problems.map { it.reason }
    }

    @Test
    fun validate_sevenClassProfile_isValid() {
        assertEquals(ProfileValidation.Valid, ProfileValidator.validate(ProfileFixture.profile()))
    }

    @Test
    fun validate_oldMetalLabel_isRejectedAsCombined() {
        val labels = listOf("Organik", "Logam_metal")
        val mapping = mapOf(
            "Organik" to WasteClass.ORGANIK,
            "Logam_metal" to WasteClass.KALENG,
        )

        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(labels, mapping)))

        assertTrue(ProfileProblemReason.COMBINED_KALENG_BESI_LABEL in reasons)
    }

    @Test
    fun validate_kalengBesiLabel_isRejectedAsCombined() {
        val labels = listOf("Organik", "Kaleng_Besi")
        val mapping = mapOf(
            "Organik" to WasteClass.ORGANIK,
            "Kaleng_Besi" to WasteClass.KALENG,
        )

        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(labels, mapping)))

        assertTrue(ProfileProblemReason.COMBINED_KALENG_BESI_LABEL in reasons)
    }

    @Test
    fun validate_classificationOnlyOutput_isRejected() {
        val output = OutputSpec(
            format = OutputFormat.CLASSIFICATION_ONLY,
            boxesNormalized = false,
            hasObjectness = false,
        )

        val reasons = reasonsOf(
            ProfileValidator.validate(ProfileFixture.profile(output = output)),
        )

        assertTrue(ProfileProblemReason.NO_LOCATION_OUTPUT in reasons)
    }

    @Test
    fun validate_labelWithoutMapping_isRejected() {
        val labels = ProfileFixture.labels + "Kertas"
        val mapping = ProfileFixture.mapping

        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(labels, mapping)))

        assertTrue(ProfileProblemReason.LABEL_NOT_MAPPED in reasons)
    }

    @Test
    fun validate_mappingForUnknownLabel_isRejected() {
        val mapping = ProfileFixture.mapping + ("Kertas" to WasteClass.KARDUS)

        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(mapping = mapping)))

        assertTrue(ProfileProblemReason.MAPPING_FOR_UNKNOWN_LABEL in reasons)
    }

    @Test
    fun validate_duplicateLabel_isRejected() {
        val labels = ProfileFixture.labels + "Organik"

        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(labels = labels)))

        assertTrue(ProfileProblemReason.DUPLICATE_LABEL in reasons)
    }

    @Test
    fun validate_emptyLabels_isRejected() {
        val reasons = reasonsOf(
            ProfileValidator.validate(ProfileFixture.profile(labels = emptyList(), mapping = emptyMap())),
        )

        assertTrue(ProfileProblemReason.NO_LABELS in reasons)
    }

    @Test
    fun validate_confidenceZero_isRejected() {
        val reasons = reasonsOf(
            ProfileValidator.validate(ProfileFixture.profile(confidenceThreshold = 0.0)),
        )

        assertTrue(ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD in reasons)
    }

    @Test
    fun validate_confidenceAboveOne_isRejected() {
        val reasons = reasonsOf(
            ProfileValidator.validate(ProfileFixture.profile(confidenceThreshold = 1.5)),
        )

        assertTrue(ProfileProblemReason.INVALID_CONFIDENCE_THRESHOLD in reasons)
    }

    @Test
    fun validate_nonPositiveInputSize_isRejected() {
        val reasons = reasonsOf(ProfileValidator.validate(ProfileFixture.profile(inputSize = 0)))

        assertTrue(ProfileProblemReason.INVALID_INPUT_SIZE in reasons)
    }

    @Test
    fun validate_unknownSchemaVersion_isRejected() {
        val profile = ProfileFixture.profile().copy(schemaVersion = ProfileFixture.SCHEMA_VERSION + 1)

        val reasons = reasonsOf(ProfileValidator.validate(profile))

        assertTrue(ProfileProblemReason.UNKNOWN_SCHEMA_VERSION in reasons)
    }

    @Test
    fun isCombinedKalengBesiLabel_separateLabels_areNotCombined() {
        assertFalse(ProfileValidator.isCombinedKalengBesiLabel("Kaleng"))
        assertFalse(ProfileValidator.isCombinedKalengBesiLabel("Besi"))
        assertTrue(ProfileValidator.isCombinedKalengBesiLabel("Kaleng+Besi"))
        assertTrue(ProfileValidator.isCombinedKalengBesiLabel("Besi_Kaleng"))
    }

    @Test
    fun coveredClasses_sevenClassProfile_coversAllClasses() {
        val covered = ProfileValidator.coveredClasses(ProfileFixture.profile())

        assertEquals(WasteClass.entries.toSet(), covered)
    }
}
