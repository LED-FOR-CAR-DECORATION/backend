package com.lumintra.backend.device.application;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.domain.service.FirmwareBinaryStorage;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.OtaSessionNotFoundException;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StartFirmwareDownloadUseCaseTest {

    private static final String TOKEN = "ab".repeat(32);
    private static final long DEVICE = 2001L;
    private static final byte[] BINARY = {(byte) 0xE9, 1, 2, 3, 4, 5, 6, 7};

    @Mock private FirmwareOtaSessionRepository sessionRepository;
    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;
    @Mock private FirmwareBinaryStorage binaryStorage;

    private StartFirmwareDownloadUseCase useCase;
    private FirmwareOtaSessionEntity session;
    private LedInstanceEntity device;
    private FirmwareVersionEntity firmware;

    @BeforeEach
    void setUp() {
        useCase = new StartFirmwareDownloadUseCase(
                new OtaDownloadAccess(sessionRepository, ledInstanceRepository, firmwareVersionRepository),
                sessionRepository, binaryStorage);
        session = FirmwareOtaSessionEntity.issue(CreateOtaSessionUseCase.hashToken(TOKEN), DEVICE, 12L,
                "0123456789abcdef0123456789abcdef", Instant.now().plus(Duration.ofMinutes(10)));
        session.setSessionId(31L);
        device = LedInstanceEntity.createWithState(3L, "SN-1", "AA:BB:CC:11:22:03", 55L, "Car",
                LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now());
        device.setInstanceId(DEVICE);
        firmware = FirmwareVersionEntity.create(3L, "1.2.0", "notes", "firmware/3/1.2.0.bin", (long) BINARY.length,
                "c".repeat(64), FirmwareStatus.ACTIVE, 70L, Instant.now());
        firmware.setFirmwareId(12L);
        when(sessionRepository.findByTokenHash(CreateOtaSessionUseCase.hashToken(TOKEN))).thenReturn(Optional.of(session));
        when(ledInstanceRepository.findById(DEVICE)).thenReturn(Optional.of(device));
        when(firmwareVersionRepository.findById(12L)).thenReturn(Optional.of(firmware));
        when(sessionRepository.claimDownloadAttempt(eq(31L), eq(OtaSessionStatus.ISSUED), eq(3), any(Instant.class)))
                .thenReturn(1);
        when(binaryStorage.open("firmware/3/1.2.0.bin"))
                .thenReturn(new FirmwareBinaryStorage.StoredBinary(BINARY.length, new ByteArrayInputStream(BINARY)));
    }

    @Test
    @DisplayName("DEV-23 UTCID01 [N]: Counts one attempt (max 3) and returns the stored size and stream")
    void execute_validToken_returnsBinary() throws IOException {
        StartFirmwareDownloadUseCase.FirmwareDownload download = useCase.execute(TOKEN);

        assertThat(download.size()).isEqualTo(BINARY.length);
        assertThat(download.content().readAllBytes()).isEqualTo(BINARY);
        verify(sessionRepository).claimDownloadAttempt(eq(31L), eq(OtaSessionStatus.ISSUED), eq(3), any(Instant.class));
    }

    @Test
    @DisplayName("DEV-23 UTCID02 [A]: Attempts used up (claim refused) returns OTA_SESSION_NOT_FOUND without opening storage")
    void execute_attemptsUsedUp_notFound() {
        when(sessionRepository.claimDownloadAttempt(anyLong(), any(), anyInt(), any())).thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(OtaSessionNotFoundException.class);
        verify(binaryStorage, never()).open(anyString());
    }

    @Test
    @DisplayName("DEV-23 UTCID03 [A]: Invalid token (DEV-22 rules) returns OTA_SESSION_NOT_FOUND without counting an attempt")
    void execute_invalidToken_notFound() {
        session.revoke();
        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(OtaSessionNotFoundException.class);
        assertThatThrownBy(() -> useCase.execute("not-a-token")).isInstanceOf(OtaSessionNotFoundException.class);
        verify(sessionRepository, never()).claimDownloadAttempt(anyLong(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("DEV-23 UTCID04 [A]: Stored size different from the release fails with a storage error and closes the stream")
    void execute_sizeMismatch_failsAndCloses() {
        AtomicBoolean closed = new AtomicBoolean();
        InputStream stream = new ByteArrayInputStream(BINARY) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(binaryStorage.open(anyString())).thenReturn(new FirmwareBinaryStorage.StoredBinary(BINARY.length + 1, stream));

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(StorageServiceException.class);
        assertThat(closed).isTrue();
    }

    @Test
    @DisplayName("DEV-23 UTCID05 [A]: Missing binary in storage propagates the storage error (attempt rolled back by the transaction)")
    void execute_missingBinary_propagates() {
        when(binaryStorage.open(anyString())).thenThrow(new StorageServiceException("missing"));

        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(StorageServiceException.class);
    }
}
