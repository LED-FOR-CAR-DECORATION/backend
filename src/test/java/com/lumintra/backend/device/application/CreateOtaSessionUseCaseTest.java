package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.CreateOtaSessionRequest;
import com.lumintra.backend.device.api.response.OtaSessionResponse;
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
import com.lumintra.backend.shared.exception.device.FirmwareNotActiveException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateOtaSessionUseCaseTest {

    private static final long CUSTOMER = 55L;
    private static final long DEVICE = 2001L;
    private static final String NONCE = "0123456789abcdef0123456789abcdef";

    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private FirmwareOtaSessionRepository sessionRepository;

    private CreateOtaSessionUseCase useCase;
    private LedInstanceEntity device;
    private FirmwareVersionEntity active;

    @BeforeEach
    void setUp() {
        useCase = new CreateOtaSessionUseCase(new CustomerDeviceAccess(ledInstanceRepository),
                firmwareVersionRepository, sessionRepository);
        device = LedInstanceEntity.createWithState(3L, "SN-1", "AA:BB:CC:00:00:01", CUSTOMER, "Car",
                LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now());
        device.setInstanceId(DEVICE);
        active = firmware(12L, 3L, "1.2.0", FirmwareStatus.ACTIVE);
        when(ledInstanceRepository.findByIdForUpdate(DEVICE)).thenReturn(Optional.of(device));
        when(firmwareVersionRepository.findById(12L)).thenReturn(Optional.of(active));
        when(sessionRepository.findByLedInstanceIdAndStatus(DEVICE, OtaSessionStatus.ISSUED)).thenReturn(List.of());
        when(sessionRepository.save(any(FirmwareOtaSessionEntity.class))).thenAnswer(inv -> {
            FirmwareOtaSessionEntity session = inv.getArgument(0);
            session.setSessionId(31L);
            return session;
        });
    }

    private static FirmwareVersionEntity firmware(long id, long modelId, String version, FirmwareStatus status) {
        FirmwareVersionEntity entity = FirmwareVersionEntity.create(modelId, version, "notes",
                "firmware/" + modelId + "/" + version + ".bin", 1_048_576L, "c".repeat(64), status, 70L, Instant.now());
        entity.setFirmwareId(id);
        return entity;
    }

    @Test
    @DisplayName("DEV-21 UTCID01 [N]: Issues a 64-hex token valid 10 minutes and stores only its SHA-256 with the nonce")
    void execute_activeFirmware_issuesSession() {
        Instant before = Instant.now();

        OtaSessionResponse response = useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(12L, NONCE));

        assertThat(response.sessionId()).isEqualTo(31L);
        assertThat(response.token()).matches("^[0-9a-f]{64}$");
        assertThat(response.expiresAt()).isCloseTo(before.plus(Duration.ofMinutes(10)), within(Duration.ofSeconds(5)));
        assertThat(response.firmware().firmwareId()).isEqualTo(12L);
        assertThat(response.firmware().version()).isEqualTo("1.2.0");
        assertThat(response.firmware().fileSizeBytes()).isEqualTo(1_048_576L);
        assertThat(response.firmware().sha256()).isEqualTo("c".repeat(64));
        assertThat(response.toString()).doesNotContain(response.token());

        ArgumentCaptor<FirmwareOtaSessionEntity> captor = ArgumentCaptor.forClass(FirmwareOtaSessionEntity.class);
        verify(sessionRepository).save(captor.capture());
        FirmwareOtaSessionEntity saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(CreateOtaSessionUseCase.hashToken(response.token()))
                .isNotEqualTo(response.token()).hasSize(64);
        assertThat(saved.getLedInstanceId()).isEqualTo(DEVICE);
        assertThat(saved.getFirmwareId()).isEqualTo(12L);
        assertThat(saved.getNonce()).isEqualTo(NONCE);
        assertThat(saved.getStatus()).isEqualTo(OtaSessionStatus.ISSUED);
        assertThat(saved.getExpiresAt()).isEqualTo(response.expiresAt());
    }

    @Test
    @DisplayName("DEV-21 UTCID02 [N]: A new session revokes the device's open sessions; each call gets a fresh token")
    void execute_existingOpenSession_isRevoked() {
        FirmwareOtaSessionEntity previous = FirmwareOtaSessionEntity.issue("f".repeat(64), DEVICE, 12L, NONCE,
                Instant.now().plusSeconds(300));
        when(sessionRepository.findByLedInstanceIdAndStatus(DEVICE, OtaSessionStatus.ISSUED)).thenReturn(List.of(previous));

        String first = useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(12L, NONCE)).token();
        String second = useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(12L, NONCE)).token();

        assertThat(previous.getStatus()).isEqualTo(OtaSessionStatus.REVOKED);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("DEV-21 UTCID03 [A]: Unknown firmware returns FIRMWARE_NOT_FOUND")
    void execute_unknownFirmware_throwsNotFound() {
        when(firmwareVersionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(404L, NONCE)))
                .isInstanceOf(FirmwareNotFoundException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-21 UTCID04 [A]: SUPERSEDED, DEPRECATED or another model's firmware returns FIRMWARE_NOT_ACTIVE")
    void execute_notActiveFirmware_throwsConflict() {
        when(firmwareVersionRepository.findById(10L)).thenReturn(Optional.of(firmware(10L, 3L, "1.0.0", FirmwareStatus.SUPERSEDED)));
        when(firmwareVersionRepository.findById(11L)).thenReturn(Optional.of(firmware(11L, 3L, "1.1.0", FirmwareStatus.DEPRECATED)));
        when(firmwareVersionRepository.findById(90L)).thenReturn(Optional.of(firmware(90L, 9L, "9.0.0", FirmwareStatus.ACTIVE)));

        for (long id : new long[]{10L, 11L, 90L}) {
            assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(id, NONCE)))
                    .isInstanceOf(FirmwareNotActiveException.class);
        }
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-21 UTCID05 [A]: Unknown device, someone else's device or a device not BOUND is rejected (row locked)")
    void execute_deviceChecks_reject() {
        when(ledInstanceRepository.findByIdForUpdate(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, 404L, new CreateOtaSessionRequest(12L, NONCE)))
                .isInstanceOf(LedInstanceNotFoundException.class);

        assertThatThrownBy(() -> useCase.execute(99L, DEVICE, new CreateOtaSessionRequest(12L, NONCE)))
                .isInstanceOf(DeviceOwnershipMismatchException.class);

        device.setStatus(LedInstanceStatus.RETURNED);
        assertThatThrownBy(() -> useCase.execute(CUSTOMER, DEVICE, new CreateOtaSessionRequest(12L, NONCE)))
                .isInstanceOf(DeviceNotBoundException.class);
        verify(ledInstanceRepository, never()).findById(any());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("DEV-21 UTCID06 [N]: Token hash is the lowercase hex SHA-256 of the token")
    void hashToken_isSha256Hex() {
        assertThat(CreateOtaSessionUseCase.hashToken("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(CreateOtaSessionUseCase.newToken()).matches("^[0-9a-f]{64}$");
    }
}
