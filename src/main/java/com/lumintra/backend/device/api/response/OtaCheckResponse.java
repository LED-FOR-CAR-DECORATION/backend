package com.lumintra.backend.device.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.device.domain.model.OtaVersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Whether a firmware update or rollback is available for a customer's LED device (DEV-10)")
@Builder
public record OtaCheckResponse(
        @Schema(description = "LED device (instance) id", example = "2001", requiredMode = Schema.RequiredMode.REQUIRED)
        Long deviceId,

        @Schema(description = "Version the device runs: the reported BLE value, else the recorded firmware; null when unknown",
                example = "1.0.0", nullable = true)
        String currentVersion,

        @Schema(description = "Catalog status of the current version; UNKNOWN when unknown or not a release of the device's model",
                example = "SUPERSEDED", requiredMode = Schema.RequiredMode.REQUIRED)
        OtaVersionStatus currentVersionStatus,

        @Schema(description = "True when the model has an ACTIVE release whose version differs from the current version",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean updateAvailable,

        @JsonProperty("isRollback")
        @Schema(name = "isRollback",
                description = "True when an update is available and the current version is higher than the ACTIVE one or DEPRECATED",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isRollback,

        @Schema(description = "The ACTIVE release to install; present only when updateAvailable is true", nullable = true)
        TargetFirmware targetFirmware
) {
    @Schema(description = "Firmware release the device should install")
    @Builder
    public record TargetFirmware(
            @Schema(description = "Firmware release id", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
            Long firmwareId,

            @Schema(description = "Semantic version", example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED)
            String version,

            @Schema(description = "Release notes", example = "Fix BLE reconnect after sleep", nullable = true)
            String changelog,

            @Schema(description = "Binary size in bytes", example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED)
            long fileSizeBytes,

            @Schema(description = "Lowercase hex SHA-256 the device verifies before switching partitions (BR-FL4-02)",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String sha256
    ) {
    }
}
