package com.lumintra.backend.device.api.response;

import java.time.OffsetDateTime;

public record FirmwareReleaseSummary(
        long firmwareId,
        long modelId,
        String version,
        String status,
        String releaseNotes,
        OffsetDateTime releasedAt) {}
