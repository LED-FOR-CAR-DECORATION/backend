package com.lumintra.backend.device.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of deprecating a firmware release (DEV-13)")
public record DeprecateFirmwareResponse(
        @Schema(description = "The release, now DEPRECATED", requiredMode = Schema.RequiredMode.REQUIRED)
        FirmwareReleaseResponse deprecated,

        @Schema(description = "The SUPERSEDED release promoted to ACTIVE (highest version of the model), or null when "
                + "the deprecated release was not ACTIVE or no SUPERSEDED release exists", nullable = true)
        FirmwareReleaseResponse newActiveFirmware,

        @Schema(description = "Customer-bound (BOUND) devices recorded on the deprecated release; they will be offered the rollback",
                example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
        long affectedDeviceCount
) {
}
