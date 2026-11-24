package com.lumintra.backend.device.application;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.shared.exception.device.OtaSessionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Resolves a device-presented OTA token (DEV-22/23) to a usable session. Every failure raises the same
 * {@link OtaSessionNotFoundException}, so the caller cannot tell an unknown token from an expired, revoked or
 * unbound one.
 */
@Component
@RequiredArgsConstructor
public class OtaDownloadAccess {

    private static final Pattern TOKEN = Pattern.compile("^[0-9a-f]{64}$");

    private final FirmwareOtaSessionRepository sessionRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final FirmwareVersionRepository firmwareVersionRepository;

    public record OtaDownload(FirmwareOtaSessionEntity session, LedInstanceEntity device, FirmwareVersionEntity firmware) {
    }

    public OtaDownload requireUsable(String token, Instant now) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new OtaSessionNotFoundException();
        }
        FirmwareOtaSessionEntity session = sessionRepository.findByTokenHash(CreateOtaSessionUseCase.hashToken(token))
                .filter(s -> s.getStatus() == OtaSessionStatus.ISSUED)
                .filter(s -> s.getExpiresAt() != null && now.isBefore(s.getExpiresAt()))
                .orElseThrow(OtaSessionNotFoundException::new);
        LedInstanceEntity device = ledInstanceRepository.findById(session.getLedInstanceId())
                .filter(d -> d.getStatus() == LedInstanceStatus.BOUND)
                .orElseThrow(OtaSessionNotFoundException::new);
        // A release pulled after the session was issued must not be installed; one superseded by a newer upload still may.
        FirmwareVersionEntity firmware = firmwareVersionRepository.findById(session.getFirmwareId())
                .filter(f -> f.getStatus() != FirmwareStatus.DEPRECATED)
                .orElseThrow(OtaSessionNotFoundException::new);
        return new OtaDownload(session, device, firmware);
    }
}
