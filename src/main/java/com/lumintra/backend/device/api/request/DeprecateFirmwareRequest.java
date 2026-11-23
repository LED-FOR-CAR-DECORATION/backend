package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Body for deprecating a firmware release (DEV-13)")
public record DeprecateFirmwareRequest(
        @Schema(description = "Why the release is withdrawn", example = "Boot loop on 64x64 panels after BLE reconnect",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "reason is required")
        @Size(max = 1000, message = "reason cannot exceed 1000 characters")
        String reason
) {
}
