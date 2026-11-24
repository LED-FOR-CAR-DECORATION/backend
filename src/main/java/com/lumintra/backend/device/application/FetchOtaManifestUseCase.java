package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.OtaManifestResponse;
import com.lumintra.backend.device.domain.service.EspMacAddresses;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * DEV-22: the device fetches the manifest for its OTA session, with the plain token as the only credential.
 * Repeat fetches within the session window are allowed; the first fetch time is recorded.
 */
@Service
@RequiredArgsConstructor
public class FetchOtaManifestUseCase {

    private static final Logger log = LoggerFactory.getLogger(FetchOtaManifestUseCase.class);

    private final OtaDownloadAccess downloadAccess;

    @Transactional
    public OtaManifestResponse execute(String token) {
        Instant now = Instant.now();
        OtaDownloadAccess.OtaDownload download = downloadAccess.requireUsable(token, now);
        boolean first = download.session().markManifestFetched(now);

        // Never log the token.
        log.info("OTA manifest served [sessionId={}] [deviceId={}] [firmwareId={}] [firstFetch={}]",
                download.session().getSessionId(), download.device().getInstanceId(),
                download.firmware().getFirmwareId(), first);
        Long size = download.firmware().getFileSizeBytes();
        return OtaManifestResponse.builder()
                .deviceId(EspMacAddresses.baseMacHexFromBleMac(download.device().getMacAddress()))
                .nonce(download.session().getNonce())
                .version(download.firmware().getVersion())
                .sha256(download.firmware().getSha256())
                .size(size != null ? size : 0L)
                .build();
    }
}
