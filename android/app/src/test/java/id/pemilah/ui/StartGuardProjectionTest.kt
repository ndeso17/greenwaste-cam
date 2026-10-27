package id.pemilah.ui

import id.pemilah.camera.CameraStatus
import id.pemilah.usb.LinkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private object GuardUiFixture {
    val monitoringReady = OperationUiState(
        stage = OperationStage.MONITORING,
        cameraStatus = CameraStatus.Ready,
        profileNameAndVersion = "Sampah v1",
        calibration = CalibrationUiState.VALID,
        activeClassesConfigured = true,
    )

    val sortingReady = monitoringReady.copy(
        stage = OperationStage.SORTING,
        linkState = LinkState.CONNECTED,
        machineConfigComplete = true,
        binMappingComplete = true,
    )
}

class StartGuardProjectionTest {

    @Test
    fun monitoring_allConditionsMet_hasNoBlocker() {
        assertTrue(StartGuardProjection.blockers(GuardUiFixture.monitoringReady).isEmpty())
        assertTrue(StartGuardProjection.canStart(GuardUiFixture.monitoringReady))
    }

    @Test
    fun monitoring_cameraNotReady_blocks() {
        val state = GuardUiFixture.monitoringReady.copy(cameraStatus = CameraStatus.Starting)

        assertEquals(
            listOf(StartBlocker.CAMERA_NOT_READY),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun monitoring_cameraPermissionMissing_blocks() {
        val state = GuardUiFixture.monitoringReady.copy(
            cameraStatus = CameraStatus.PermissionRequired,
        )

        assertTrue(StartBlocker.CAMERA_NOT_READY in StartGuardProjection.blockers(state))
    }

    @Test
    fun monitoring_missingProfile_blocks() {
        val state = GuardUiFixture.monitoringReady.copy(profileNameAndVersion = null)

        assertEquals(
            listOf(StartBlocker.PROFILE_MISSING),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun monitoring_calibrationNeedsCheck_doesNotBlock() {
        // D-11: kalibrasi hanya syarat pemilahan, bukan pemantauan.
        val state = GuardUiFixture.monitoringReady.copy(calibration = CalibrationUiState.NEEDS_CHECK)

        assertTrue(StartGuardProjection.blockers(state).isEmpty())
    }

    @Test
    fun monitoring_missingCalibration_doesNotBlock() {
        val state = GuardUiFixture.monitoringReady.copy(calibration = CalibrationUiState.NONE)

        assertTrue(StartGuardProjection.blockers(state).isEmpty())
    }

    @Test
    fun monitoring_cameraAndProfileOnly_canStart() {
        // Keadaan minimum yang diminta pengguna: kamera siap + profil/model aktif.
        val state = OperationUiState(
            stage = OperationStage.MONITORING,
            cameraStatus = CameraStatus.Ready,
            profileNameAndVersion = "resnet18-7kelas v1",
            calibration = CalibrationUiState.NONE,
            activeClassesConfigured = false,
            machineConfigComplete = false,
            binMappingComplete = false,
            linkState = LinkState.DISCONNECTED,
        )

        assertTrue(StartGuardProjection.blockers(state).isEmpty())
        assertTrue(StartGuardProjection.canStart(state))
    }

    @Test
    fun monitoring_usbDisconnected_doesNotBlock() {
        // USB bukan syarat mulai pemantauan (OperationScreen.xml modes.monitoring).
        val state = GuardUiFixture.monitoringReady.copy(linkState = LinkState.DISCONNECTED)

        assertTrue(StartGuardProjection.blockers(state).isEmpty())
    }

    @Test
    fun monitoring_backendUnavailable_doesNotBlock() {
        val state = GuardUiFixture.monitoringReady.copy(backend = BackendUiState.UNAVAILABLE)

        assertTrue(StartGuardProjection.blockers(state).isEmpty())
    }

    @Test
    fun sorting_allConditionsMet_hasNoBlocker() {
        assertTrue(StartGuardProjection.blockers(GuardUiFixture.sortingReady).isEmpty())
    }

    @Test
    fun sorting_usbDisconnected_blocks() {
        val state = GuardUiFixture.sortingReady.copy(linkState = LinkState.DISCONNECTED)

        assertEquals(
            listOf(StartBlocker.CONTROLLER_DISCONNECTED),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun sorting_incompleteConfig_blocks() {
        val state = GuardUiFixture.sortingReady.copy(machineConfigComplete = false)

        assertEquals(
            listOf(StartBlocker.CONFIG_INCOMPLETE),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun sorting_incompleteBinMapping_blocks() {
        val state = GuardUiFixture.sortingReady.copy(binMappingComplete = false)

        assertEquals(
            listOf(StartBlocker.BIN_MAPPING_INCOMPLETE),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun sorting_reportsEveryMissingCondition() {
        val state = GuardUiFixture.monitoringReady.copy(
            stage = OperationStage.SORTING,
            linkState = LinkState.DISCONNECTED,
            machineConfigComplete = false,
            binMappingComplete = false,
        )

        assertEquals(
            listOf(
                StartBlocker.CONTROLLER_DISCONNECTED,
                StartBlocker.CONFIG_INCOMPLETE,
                StartBlocker.BIN_MAPPING_INCOMPLETE,
            ),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun sorting_missingCalibration_blocks() {
        val state = GuardUiFixture.sortingReady.copy(calibration = CalibrationUiState.NONE)

        assertEquals(
            listOf(StartBlocker.CALIBRATION_MISSING),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun sorting_missingActiveClasses_blocks() {
        val state = GuardUiFixture.sortingReady.copy(activeClassesConfigured = false)

        assertEquals(
            listOf(StartBlocker.NO_ACTIVE_CLASS),
            StartGuardProjection.blockers(state),
        )
    }

    @Test
    fun running_disablesStartAndEnablesStop() {
        val state = GuardUiFixture.monitoringReady.copy(isRunning = true)

        assertFalse(StartGuardProjection.canStart(state))
        assertTrue(StartGuardProjection.canStop(state))
    }

    @Test
    fun stopped_enablesStartAndDisablesStop() {
        assertTrue(StartGuardProjection.canStart(GuardUiFixture.monitoringReady))
        assertFalse(StartGuardProjection.canStop(GuardUiFixture.monitoringReady))
    }

    @Test
    fun runningWithBlockers_stillCannotStart() {
        val state = GuardUiFixture.sortingReady.copy(
            isRunning = true,
            calibration = CalibrationUiState.NEEDS_CHECK,
        )

        assertFalse(StartGuardProjection.canStart(state))
    }
}
