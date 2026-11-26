package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.ReportInstalledFirmwareRequest;
import com.lumintra.backend.device.api.response.InstalledFirmwareResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.DeviceNotBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.FirmwareModelMismatchException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportInstalledFirmwareUseCaseTest {

    private static final long CUSTOMER = 55L;
    private static final long DEVICE = 2001L;
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private FirmwareOtaSessionRepository sessionRepository;

    private ReportInstalledFirmwareUseCase useCase;
    private LedInstanceEntity device;

    @BeforeEach
    void setUp() {
        useCase = new ReportInstalledFirmwareUseCase(new CustomerDeviceAccess(ledInstanceRepository),
                firmwareVersionRepository, sessionRepository);
        device = LedInstanceEntity.createWithState(3L, "SN-1", "AA:BB:CC:00:00:01", CUSTOMER, "Car",
                LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now());
        device.setInstanceId(DEVICE);
        device.setCurrentFirmwareId(10L);
        when(ledInstanceRepository.findByIdForUpdate(DEVICE)).thenReturn(Optional.of(device));
        stubFirmware(10L, 3L, "1.0.0", FirmwareStatus.SUPERSEDED);
        stubFirmware(12L, 3L, "1.2.0", FirmwareStatus.ACTIVE);
        when(sessionRepository.findByLedInstanceIdAndStatus(DEVICE, OtaSessionStatus.ISSUED)).thenReturn(List.of());
    }

    private void stubFirmware(long id, long modelId, String version, FirmwareStatus status) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(modelId, version, "notes",
                "firmware/" + modelId + "/" + version + ".bin", 1024L, "c".repeat(64), status, 70L, Instant.now());
        entity.setFirmwareId(id);
        when(firmwareVersionRepository.findById(id)).thenReturn(Optional.of(entity));
    }

    private static FirmwareOtaSessionEntity session(long firmwareId) {
        return FirmwareOtaSessionEntity.issue("f".repeat(64), DEVICE, firmwareId, NONCE, Instant.now().plusSeconds(300));
    }

    @Test
    @DisplayName("DEV-10B UTCID01 [N]: Records the release and update time, and returns deviceId, firmwareId, version, status, time")
    void execute_newRelease_recordsIt() {
        Instant before = Instant.now();

        InstalledFirmwareResponse response = useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(12L));

        assertThat(device.getCurrentFirmwareId()).isEqualTo(12L);
        assertThat(device.getFirmwareUpdatedAt()).isCloseTo(before, within(Duration.ofSeconds(5)));
        assertThat(response.deviceId()).isEqualTo(DEVICE);
        assertThat(response.firmwareId()).isEqualTo(12L);
        assertThat(response.version()).isEqualTo("1.2.0");
        assertThat(response.firmwareStatus()).isEqualTo(FirmwareStatus.ACTIVE);
        assertThat(response.firmwareUpdatedAt()).isEqualTo(device.getFirmwareUpdatedAt());
    }

    @Test
    @DisplayName("DEV-10B UTCID02 [N]: The open session for the reported release becomes COMPLETED; one for another release stays open")
    void execute_matchingOpenSession_isCompleted() {
        FirmwareOtaSessionEntity matching = session(12L);
        FirmwareOtaSessionEntity other = session(11L);
        when(sessionRepository.findByLedInstanceIdAndStatus(DEVICE, OtaSessionStatus.ISSUED))
                .thenReturn(List.of(matching, other));

        useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(12L));

        assertThat(matching.getStatus()).isEqualTo(OtaSessionStatus.COMPLETED);
        assertThat(other.getStatus()).isEqualTo(OtaSessionStatus.ISSUED);
    }

    @Test
    @DisplayName("DEV-10B UTCID03 [N]: Reporting the release already recorded returns 200 data and changes nothing")
    void execute_sameRelease_isNoOp() {
        Instant earlier = Instant.parse("2026-11-01T00:00:00Z");
        device.recordInstalledFirmware(12L, earlier);
        FirmwareOtaSessionEntity open = session(12L);
        when(sessionRepository.findByLedInstanceIdAndStatus(DEVICE, OtaSessionStatus.ISSUED)).thenReturn(List.of(open));

        InstalledFirmwareResponse response = useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(12L));

        assertThat(response.firmwareUpdatedAt()).isEqualTo(earlier);
        assertThat(device.getFirmwareUpdatedAt()).isEqualTo(earlier);
        assertThat(open.getStatus()).isEqualTo(OtaSessionStatus.ISSUED);
        verify(sessionRepository, never()).findByLedInstanceIdAndStatus(anyLong(), any());
    }

    @Test
    @DisplayName("DEV-10B UTCID04 [B]: A DEPRECATED or SUPERSEDED release of the same model is accepted")
    void execute_anyStatusAccepted() {
        stubFirmware(11L, 3L, "1.1.0", FirmwareStatus.DEPRECATED);

        assertThat(useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(11L)).firmwareStatus())
                .isEqualTo(FirmwareStatus.DEPRECATED);
        device.setCurrentFirmwareId(12L);
        assertThat(useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(10L)).firmwareStatus())
                .isEqualTo(FirmwareStatus.SUPERSEDED);
        assertThat(device.getCurrentFirmwareId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("DEV-10B UTCID05 [A]: Unknown firmware returns FIRMWARE_NOT_FOUND; another model's returns FIRMWARE_MODEL_MISMATCH")
    void execute_invalidFirmware_rejected() {
        when(firmwareVersionRepository.findById(404L)).thenReturn(Optional.empty());
        stubFirmware(90L, 9L, "9.0.0", FirmwareStatus.ACTIVE);

        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(404L)))
                .isInstanceOf(FirmwareNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(90L)))
                .isInstanceOf(FirmwareModelMismatchException.class);
        assertThat(device.getCurrentFirmwareId()).isEqualTo(10L);
        assertThat(device.getFirmwareUpdatedAt()).isNull();
    }

    @Test
    @DisplayName("DEV-10B UTCID06 [A]: Unknown device, someone else's device or a device not BOUND is rejected (row locked)")
    void execute_deviceChecks_reject() {
        when(ledInstanceRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, 404L, new ReportInstalledFirmwareRequest(12L)))
                .isInstanceOf(LedInstanceNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute(99L, DEVICE, new ReportInstalledFirmwareRequest(12L)))
                .isInstanceOf(DeviceOwnershipMismatchException.class);
        device.setStatus(LedInstanceStatus.RETURNED);
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new ReportInstalledFirmwareRequest(12L)))
                .isInstanceOf(DeviceNotBoundException.class);
        verify(ledInstanceRepository, never()).findById(any());
        assertThat(device.getCurrentFirmwareId()).isEqualTo(10L);
    }
}
