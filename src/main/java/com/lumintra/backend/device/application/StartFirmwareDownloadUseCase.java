package com.lumintra.backend.device.application;

import com.lumintra.backend.device.domain.model.OtaSessionStatus;
import com.lumintra.backend.device.domain.service.FirmwareBinaryStorage;
import com.lumintra.backend.device.infrastructure.persistence.entity.FirmwareVersionEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.FirmwareOtaSessionRepository;
import com.lumintra.backend.shared.exception.device.OtaSessionNotFoundException;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

/**
 * DEV-23: the device downloads the firmware binary for its OTA session, with the plain token as the only
 * credential. Each call counts as one attempt (at most {@value #MAX_DOWNLOAD_ATTEMPTS} per session). The stored
 * object is opened before anything is sent, so a missing or mismatched binary fails with an error instead of a
 * truncated download; if opening fails, the attempt is rolled back.
 */
@Service
@RequiredArgsConstructor
public class StartFirmwareDownloadUseCase {

    private static final Logger log = LoggerFactory.getLogger(StartFirmwareDownloadUseCase.class);
    public static final int MAX_DOWNLOAD_ATTEMPTS = 3;

    private final OtaDownloadAccess downloadAccess;
    private final FirmwareOtaSessionRepository sessionRepository;
    private final FirmwareBinaryStorage binaryStorage;

    /** The binary to stream: exact size for Content-Length and an open stream the caller must close. */
    public record FirmwareDownload(long size, InputStream content) {
    }

    @Transactional
    public FirmwareDownload execute(String token) {
        Instant now = Instant.now();
        OtaDownloadAccess.OtaDownload download = downloadAccess.requireUsable(token, now);
        Long sessionId = download.session().getSessionId();
        int previousAttempts = download.session().getDownloadAttempts();

        if (sessionRepository.claimDownloadAttempt(sessionId, OtaSessionStatus.ISSUED, MAX_DOWNLOAD_ATTEMPTS, now) != 1) {
            log.warn("OTA download refused [sessionId={}] [attempts={}] [max={}]",
                    sessionId, previousAttempts, MAX_DOWNLOAD_ATTEMPTS);
            throw new OtaSessionNotFoundException();
        }

        FirmwareVersionEntity firmware = download.firmware();
        FirmwareBinaryStorage.StoredBinary binary = binaryStorage.open(firmware.getStorageObjectKey());
        Long expectedSize = firmware.getFileSizeBytes();
        if (expectedSize == null || binary.size() != expectedSize) {
            closeQuietly(binary.content());
            log.error("Stored firmware size does not match the release [firmwareId={}] [expected={}] [stored={}]",
                    firmware.getFirmwareId(), expectedSize, binary.size());
            throw new StorageServiceException("Stored firmware binary does not match the release.");
        }

        // Never log the token.
        log.info("OTA binary download started [sessionId={}] [deviceId={}] [firmwareId={}] [attempt={}/{}] [size={}]",
                sessionId, download.device().getInstanceId(), firmware.getFirmwareId(), previousAttempts + 1,
                MAX_DOWNLOAD_ATTEMPTS, binary.size());
        return new FirmwareDownload(binary.size(), binary.content());
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
