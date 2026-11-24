package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.CreateOtaSessionRequest;
import com.lumintra.backend.device.api.response.OtaSessionResponse;
import com.lumintra.backend.device.domain.model.FirmwareStatus;
import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareOtaSessionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareVersionRepository;
import com.lumintra.backend.shared.exception.device.FirmwareNotActiveException;
import com.lumintra.backend.shared.exception.device.FirmwareNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * DEV-21: issues a single-use, 10-minute download token for a customer's BOUND device and the model's
 * ACTIVE firmware. The app hands the token to the device over BLE ({@code ota_start}); the device then
 * fetches the manifest and binary itself (DEV-22/23). Only the token's SHA-256 is stored, and any open
 * session of the device is revoked.
 */
@Service
@RequiredArgsConstructor
public class CreateOtaSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateOtaSessionUseCase.class);
    static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private final CustomerDeviceAccess deviceAccess;
    private final FirmwareVersionRepository firmwareVersionRepository;
    private final FirmwareOtaSessionRepository sessionRepository;

    @Transactional
    public OtaSessionResponse execute(Long customerId, Long deviceId, CreateOtaSessionRequest request) {
        LedInstanceEntity device = deviceAccess.requireOwnedBoundDeviceForUpdate(customerId, deviceId);

        FirmwareVersionEntity firmware = firmwareVersionRepository.findById(request.firmwareId())
                .orElseThrow(() -> new FirmwareNotFoundException(request.firmwareId()));
        if (!Objects.equals(firmware.getModelId(), device.getModelId()) || firmware.getStatus() != FirmwareStatus.ACTIVE) {
            throw new FirmwareNotActiveException(
                    "Firmware " + firmware.getFirmwareId() + " is not the ACTIVE release of this device's model.");
        }

        List<FirmwareOtaSessionEntity> open = sessionRepository.findByLedInstanceIdAndStatus(
                device.getInstanceId(), OtaSessionStatus.ISSUED);
        open.forEach(FirmwareOtaSessionEntity::revoke);

        String token = newToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL);
        FirmwareOtaSessionEntity session = sessionRepository.save(FirmwareOtaSessionEntity.issue(
                hashToken(token), device.getInstanceId(), firmware.getFirmwareId(), request.nonce(), expiresAt));

        // Never log the token.
        log.info("OTA session issued [sessionId={}] [deviceId={}] [firmwareId={}] [version={}] [revokedSessions={}] [expiresAt={}]",
                session.getSessionId(), device.getInstanceId(), firmware.getFirmwareId(), firmware.getVersion(),
                open.size(), expiresAt);
        return OtaSessionResponse.builder()
                .sessionId(session.getSessionId())
                .token(token)
                .expiresAt(expiresAt)
                .firmware(OtaSessionResponse.SessionFirmware.builder()
                        .firmwareId(firmware.getFirmwareId())
                        .version(firmware.getVersion())
                        .fileSizeBytes(firmware.getFileSizeBytes() != null ? firmware.getFileSizeBytes() : 0L)
                        .sha256(firmware.getSha256())
                        .build())
                .build();
    }

    /** 32 random bytes as 64 lowercase hex characters, the format the device firmware accepts. */
    static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }

    /** Lowercase hex SHA-256 of the token; DEV-22/23 look sessions up by this value. */
    public static String hashToken(String token) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
