package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.OtaManifestResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.OtaSessionNotFoundException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FetchOtaManifestUseCaseTest {

    private static final String TOKEN = "ab".repeat(32);
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static final long DEVICE = 2001L;

    @Mock private FirmwareOtaSessionRepository sessionRepository;
    @Mock private LedInstanceRepository ledInstanceRepository;
    @Mock private FirmwareVersionRepository firmwareVersionRepository;

    private FetchOtaManifestUseCase useCase;
    private FirmwareOtaSessionEntity session;
    private LedInstanceEntity device;
    private FirmwareVersionEntity firmware;

    @BeforeEach
    void setUp() {
        useCase = new FetchOtaManifestUseCase(
                new OtaDownloadAccess(sessionRepository, ledInstanceRepository, firmwareVersionRepository));
        session = FirmwareOtaSessionEntity.issue(CreateOtaSessionUseCase.hashToken(TOKEN), DEVICE, 12L, NONCE,
                Instant.now().plus(Duration.ofMinutes(10)));
        session.setSessionId(31L);
        device = LedInstanceEntity.createWithState(3L, "SN-1", "aa:bb:cc:11:22:03", null, 55L, "Car",
                LedInstanceStatus.BOUND, Instant.now(), Instant.now(), Instant.now());
        device.setInstanceId(DEVICE);
        firmware = FirmwareVersionEntity.create(3L, "1.2.0", "notes", "firmware/3/1.2.0.bin", 1_048_576L,
                "c".repeat(64), FirmwareStatus.ACTIVE, 70L, Instant.now());
        firmware.setFirmwareId(12L);
        when(sessionRepository.findByTokenHash(CreateOtaSessionUseCase.hashToken(TOKEN))).thenReturn(Optional.of(session));
        when(ledInstanceRepository.findById(DEVICE)).thenReturn(Optional.of(device));
        when(firmwareVersionRepository.findById(12L)).thenReturn(Optional.of(firmware));
    }

    @Test
    @DisplayName("DEV-22 UTCID01 [N]: Returns base MAC, nonce, version, sha256 and size, and records the first fetch")
    void execute_validToken_returnsManifest() {
        Instant before = Instant.now();

        OtaManifestResponse manifest = useCase.execute(TOKEN);

        assertThat(manifest.deviceId()).isEqualTo("AABBCC112201");
        assertThat(manifest.nonce()).isEqualTo(NONCE);
        assertThat(manifest.version()).isEqualTo("1.2.0");
        assertThat(manifest.sha256()).isEqualTo("c".repeat(64));
        assertThat(manifest.size()).isEqualTo(1_048_576L);
        assertThat(session.getManifestFetchedAt()).isCloseTo(before, within(Duration.ofSeconds(5)));
        verify(sessionRepository).findByTokenHash(CreateOtaSessionUseCase.hashToken(TOKEN));
    }

    @Test
    @DisplayName("DEV-22 UTCID02 [N]: Repeat fetches within the window succeed and keep the first fetch time")
    void execute_repeatFetch_keepsFirstTime() {
        useCase.execute(TOKEN);
        Instant first = session.getManifestFetchedAt();

        OtaManifestResponse again = useCase.execute(TOKEN);

        assertThat(again.nonce()).isEqualTo(NONCE);
        assertThat(session.getManifestFetchedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("DEV-22 UTCID03 [A]: Unknown or malformed token returns OTA_SESSION_NOT_FOUND")
    void execute_unknownOrMalformedToken_notFound() {
        when(sessionRepository.findByTokenHash(CreateOtaSessionUseCase.hashToken("cd".repeat(32)))).thenReturn(Optional.empty());

        for (String token : new String[]{"cd".repeat(32), TOKEN.toUpperCase(), TOKEN.substring(1), "", null}) {
            assertThatThrownBy(() -> useCase.execute(token)).isInstanceOf(OtaSessionNotFoundException.class);
        }
        verify(sessionRepository, never()).findByTokenHash(CreateOtaSessionUseCase.hashToken(TOKEN.toUpperCase()));
    }

    @Test
    @DisplayName("DEV-22 UTCID04 [A]: Expired, revoked or completed session returns the same OTA_SESSION_NOT_FOUND")
    void execute_unusableSession_notFound() {
        FirmwareOtaSessionEntity expired = FirmwareOtaSessionEntity.issue("x", DEVICE, 12L, NONCE, Instant.now().minusSeconds(1));
        FirmwareOtaSessionEntity revoked = FirmwareOtaSessionEntity.issue("y", DEVICE, 12L, NONCE, Instant.now().plusSeconds(300));
        revoked.revoke();

        for (FirmwareOtaSessionEntity bad : new FirmwareOtaSessionEntity[]{expired, revoked}) {
            when(sessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(bad));
            assertThatThrownBy(() -> useCase.execute(TOKEN))
                    .isInstanceOf(OtaSessionNotFoundException.class)
                    .hasMessage("OTA session not found or no longer valid.");
            assertThat(bad.getManifestFetchedAt()).isNull();
        }
    }

    @Test
    @DisplayName("DEV-22 UTCID05 [A]: Device no longer BOUND, or missing, returns OTA_SESSION_NOT_FOUND")
    void execute_deviceNotBound_notFound() {
        device.setStatus(LedInstanceStatus.RETURNED);
        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(OtaSessionNotFoundException.class);

        when(ledInstanceRepository.findById(DEVICE)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(OtaSessionNotFoundException.class);
        assertThat(session.getManifestFetchedAt()).isNull();
    }

    @Test
    @DisplayName("DEV-22 UTCID06 [B]: Firmware deprecated after issue is refused; one superseded by a newer upload is still served")
    void execute_firmwareStatusAfterIssue() {
        firmware.setStatus(FirmwareStatus.SUPERSEDED);
        assertThat(useCase.execute(TOKEN).version()).isEqualTo("1.2.0");

        firmware.setStatus(FirmwareStatus.DEPRECATED);
        assertThatThrownBy(() -> useCase.execute(TOKEN)).isInstanceOf(OtaSessionNotFoundException.class);
    }
}
