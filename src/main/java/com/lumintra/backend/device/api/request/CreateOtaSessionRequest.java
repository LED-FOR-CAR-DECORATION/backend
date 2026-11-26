package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request a one-time OTA download session for a device (DEV-21)")
public record CreateOtaSessionRequest(
        @Schema(description = "The model's ACTIVE firmware release, as returned by the OTA check", example = "12",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "firmwareId is required")
        @Positive(message = "firmwareId must be a positive integer")
        Long firmwareId,

        @Schema(description = "Nonce the device returned from get_ota_info: exactly 32 lowercase hex characters",
                example = "9f86d081884c7d659a2feaa0c55ad015", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "nonce is required")
        @Pattern(regexp = "^[0-9a-f]{32}$", message = "nonce must be exactly 32 lowercase hex characters")
        String nonce
) {
}
