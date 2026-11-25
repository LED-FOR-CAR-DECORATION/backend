package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Firmware release the device reports it is now running (DEV-10B)")
public record ReportInstalledFirmwareRequest(
        @Schema(description = "Installed firmware release id; any status is accepted, including DEPRECATED",
                example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "firmwareId is required")
        @Positive(message = "firmwareId must be a positive integer")
        Long firmwareId
) {
}
