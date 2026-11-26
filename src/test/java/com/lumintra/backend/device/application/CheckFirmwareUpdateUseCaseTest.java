package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.OtaCheckResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaVersionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CheckFirmwareUpdateUseCaseTest {

    private static final long CUSTOMER = 55L;
    private static final long DEVICE = 2001L;

    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;

    private CheckFirmwareUpdateUseCase useCase;
    private LedInstanceEntity device;

    private final FirmwareVersionEntity v100 = release(10L, "1.0.0", FirmwareStatus.SUPERSEDED);
    private final FirmwareVersionEntity v110 = release(11L, "1.1.0", FirmwareStatus.ACTIVE);
    private final FirmwareVersionEntity v120 = release(12L, "1.2.0", FirmwareStatus.DEPRECATED);

    @BeforeEach
    void setUp() {
        useCase = new CheckFirmwareUpdateUseCase(new CustomerDeviceAccess(ledInstanceRepository), firmwareVersionRepository);
        device = LedInstanceEntity.createWithState(3L, "SN-1", "AA:BB:CC:00:00:01", CUSTOMER, "Car",
                LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now());
        device.setInstanceId(DEVICE);
        when(ledInstanceRepository.findById(DEVICE)).thenReturn(Optional.of(device));
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of(v100, v110, v120));
        when(firmwareVersionRepository.findById(10L)).thenReturn(Optional.of(v100));
    }

    private static FirmwareVersionEntity release(long id, String version, FirmwareStatus status) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(3L, version, "notes " + version,
                "firmware/3/" + version + ".bin", 1000L + id, "b".repeat(64), status, 70L, Instant.now());
        entity.setFirmwareId(id);
        return entity;
    }

    @Test
    @DisplayName("DEV-10 UTCID01 [N]: Older SUPERSEDED version reported over BLE gets the ACTIVE release as an update")
    void execute_olderVersion_offersUpdate() {
        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, "1.0.0");

        assertThat(response.deviceId()).isEqualTo(DEVICE);
        assertThat(response.currentVersion()).isEqualTo("1.0.0");
        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.SUPERSEDED);
        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.isRollback()).isFalse();
        assertThat(response.targetFirmware().firmwareId()).isEqualTo(11L);
        assertThat(response.targetFirmware().version()).isEqualTo("1.1.0");
        assertThat(response.targetFirmware().changelog()).isEqualTo("notes 1.1.0");
        assertThat(response.targetFirmware().fileSizeBytes()).isEqualTo(1011L);
        assertThat(response.targetFirmware().sha256()).hasSize(64);
    }

    @Test
    @DisplayName("DEV-10 UTCID02 [N]: Device already on the ACTIVE version has no update and no targetFirmware")
    void execute_onActiveVersion_noUpdate() {
        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, "1.1.0");

        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.ACTIVE);
        assertThat(response.updateAvailable()).isFalse();
        assertThat(response.isRollback()).isFalse();
        assertThat(response.targetFirmware()).isNull();
    }

    @Test
    @DisplayName("DEV-10 UTCID03 [N]: Device on a DEPRECATED (higher) version is offered a rollback to the ACTIVE one")
    void execute_onDeprecatedVersion_offersRollback() {
        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, "1.2.0");

        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.DEPRECATED);
        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.isRollback()).isTrue();
        assertThat(response.targetFirmware().version()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("DEV-10 UTCID04 [B]: Higher version not in the catalog is UNKNOWN and still a rollback")
    void execute_higherUnknownVersion_isRollback() {
        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, "2.0.0");

        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.UNKNOWN);
        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.isRollback()).isTrue();
    }

    @Test
    @DisplayName("DEV-10 UTCID05 [N]: Without currentVersion the recorded firmware's version is used")
    void execute_noReportedVersion_usesRecordedFirmware() {
        device.setCurrentFirmwareId(10L);

        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, null);

        assertThat(response.currentVersion()).isEqualTo("1.0.0");
        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.SUPERSEDED);
        assertThat(response.updateAvailable()).isTrue();
    }

    @Test
    @DisplayName("DEV-10 UTCID06 [B]: Unknown current version gets the ACTIVE release, not as a rollback")
    void execute_unknownVersion_offersUpdateWithoutRollback() {
        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, "  ");

        assertThat(response.currentVersion()).isNull();
        assertThat(response.currentVersionStatus()).isEqualTo(OtaVersionStatus.UNKNOWN);
        assertThat(response.updateAvailable()).isTrue();
        assertThat(response.isRollback()).isFalse();
        assertThat(response.targetFirmware().version()).isEqualTo("1.1.0");
    }

    @Test
    @DisplayName("DEV-10 UTCID07 [B]: Model without an ACTIVE release reports no update")
    void execute_noActiveRelease_noUpdate() {
        when(firmwareVersionRepository.findByModelId(3L)).thenReturn(List.of(v100, v120));

        OtaCheckResponse response = useCase.execute(CUSTOMER, DEVICE, null);

        assertThat(response.updateAvailable()).isFalse();
        assertThat(response.isRollback()).isFalse();
        assertThat(response.targetFirmware()).isNull();
    }

    @Test
    @DisplayName("DEV-10 UTCID08 [A]: Unknown device, someone else's device or a device not BOUND is rejected")
    void execute_deviceChecks_reject() {
        when(ledInstanceRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, 404L, null)).isInstanceOf(LedInstanceNotFoundException.class);

        assertThatThrownBy(() -> useCase.execute(99L, DEVICE, null)).isInstanceOf(DeviceOwnershipMismatchException.class);

        device.setStatus(LedInstanceStatus.RETURNED);
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, null)).isInstanceOf(DeviceNotBoundException.class);
    }

    @Test
    @DisplayName("DEV-10 UTCID09 [N]: The check is read-only and never changes the recorded firmware")
    void execute_isReadOnly() {
        device.setCurrentFirmwareId(10L);

        useCase.execute(CUSTOMER, DEVICE, "1.2.0");

        assertThat(device.getCurrentFirmwareId()).isEqualTo(10L);
        verify(ledInstanceRepository, never()).save(any());
        verify(firmwareVersionRepository, never()).save(any());
    }
}
