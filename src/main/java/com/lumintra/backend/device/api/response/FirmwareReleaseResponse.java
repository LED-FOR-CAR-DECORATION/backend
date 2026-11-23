package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "A firmware release of an LED model")
@Builder
public record FirmwareReleaseResponse(
        @Schema(description = "Firmware release id", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        Long firmwareId,

        @Schema(description = "LED model id", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        Long modelId,

        @Schema(description = "LED model name", example = "Lumintra Matrix 64x64 Pro", requiredMode = Schema.RequiredMode.REQUIRED)
        String modelName,

        @Schema(description = "Semantic version", example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED)
        String version,

        @Schema(description = "Release status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        FirmwareStatus status,

        @Schema(description = "Release notes", example = "Fix BLE reconnect after sleep", requiredMode = Schema.RequiredMode.REQUIRED)
        String changelog,

        @Schema(description = "Binary size in bytes", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
        long fileSizeBytes,

        @Schema(description = "Lowercase hex SHA-256 of the binary, computed by the server",
                example = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", requiredMode = Schema.RequiredMode.REQUIRED)
        String sha256,

        @Schema(description = "Full name of the admin who uploaded the release", example = "Nguyen Van A", nullable = true)
        String uploadedBy,

        @Schema(description = "Upload time (UTC)", example = "2026-11-21T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant uploadedAt,

        @Schema(description = "Deprecation time (UTC)", nullable = true)
        Instant deprecatedAt,

        @Schema(description = "Full name of the admin who deprecated the release", nullable = true)
        String deprecatedBy,

        @Schema(description = "Why the release was deprecated", nullable = true)
        String deprecationReason,

        @Schema(description = "Number of customer-bound (BOUND) devices recorded on this release", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long installedDeviceCount
) {
}
