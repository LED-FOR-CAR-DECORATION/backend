package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The uploaded release (same fields as {@link FirmwareReleaseResponse}, flattened) plus the id of
 * the release it replaced as ACTIVE.
 */
@Schema(description = "Uploaded firmware release and the previously ACTIVE release it superseded")
public record UploadFirmwareResponse(
        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long firmwareId,
        @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) Long modelId,
        @Schema(example = "Lumintra Matrix 64x64 Pro", requiredMode = Schema.RequiredMode.REQUIRED) String modelName,
        @Schema(example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED) String version,
        @Schema(example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED) FirmwareStatus status,
        @Schema(example = "Fix BLE reconnect after sleep", requiredMode = Schema.RequiredMode.REQUIRED) String changelog,
        @Schema(example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED) long fileSizeBytes,
        @Schema(description = "Lowercase hex SHA-256 computed by the server", requiredMode = Schema.RequiredMode.REQUIRED) String sha256,
        @Schema(example = "Nguyen Van A", nullable = true) String uploadedBy,
        @Schema(example = "2026-11-21T08:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED) Instant uploadedAt,
        @Schema(nullable = true) Instant deprecatedAt,
        @Schema(nullable = true) String deprecatedBy,
        @Schema(nullable = true) String deprecationReason,
        @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED) long installedDeviceCount,
        @Schema(description = "Id of the release that was ACTIVE before this upload, now SUPERSEDED", example = "11", nullable = true)
        Long supersededFirmwareId
) {
    public static UploadFirmwareResponse of(FirmwareReleaseResponse release, Long supersededFirmwareId) {
        return new UploadFirmwareResponse(
                release.firmwareId(), release.modelId(), release.modelName(), release.version(), release.status(),
                release.changelog(), release.fileSizeBytes(), release.sha256(), release.uploadedBy(),
                release.uploadedAt(), release.deprecatedAt(), release.deprecatedBy(), release.deprecationReason(),
                release.installedDeviceCount(), supersededFirmwareId);
    }
}
