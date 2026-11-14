package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.FirmwareUpdateResponse;
import com.lumintra.backend.device.infrastructure.persistence.FirmwareUpdateLookup;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckFirmwareUpdateUseCase {
    public static final String VERSION_PATTERN = "[0-9]{1,9}(?:\\.[0-9]{1,9}){0,2}";

    private final FirmwareUpdateLookup lookup;

    @Transactional(readOnly = true)
    public FirmwareUpdateResponse execute(String subject, String deviceId, String currentVersion) {
        long ownerId;
        try {
            ownerId = Long.parseLong(subject);
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }
        if (ownerId <= 0) {
            throw new InvalidCredentialsException("Invalid authenticated account.");
        }

        String latest = lookup.activeVersion(deviceId, ownerId);
        if (latest == null) {
            return new FirmwareUpdateResponse(false, currentVersion, null);
        }
        if (!latest.matches(VERSION_PATTERN)) {
            throw new IllegalStateException("Active firmware has an unsupported version format.");
        }
        return new FirmwareUpdateResponse(isNewer(latest, currentVersion), currentVersion, latest);
    }

    private boolean isNewer(String latest, String current) {
        String[] candidate = latest.split("\\.");
        String[] installed = current.split("\\.");
        for (int i = 0; i < 3; i++) {
            int left = i < candidate.length ? Integer.parseInt(candidate[i]) : 0;
            int right = i < installed.length ? Integer.parseInt(installed[i]) : 0;
            if (left != right) {
                return left > right;
            }
        }
        return false;
    }
}
