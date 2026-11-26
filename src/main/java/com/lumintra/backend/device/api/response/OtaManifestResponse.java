package com.lumintra.backend.device.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/** DEV-22 manifest the device checks before downloading. The firmware reads these exact field names under {@code data}. */
@Builder
@Schema(description = "OTA manifest for one download session")
public record OtaManifestResponse(
        @Schema(description = "Device base (eFuse) MAC, 12 uppercase hex characters without colons", example = "AABBCC112201")
        String deviceId,
        @Schema(description = "Nonce the device supplied for this session", example = "0123456789abcdef0123456789abcdef")
        String nonce,
        @Schema(description = "Target firmware version", example = "1.2.0")
        String version,
        @Schema(description = "Lowercase hex SHA-256 of the binary")
        String sha256,
        @Schema(description = "Binary size in bytes", example = "1048576")
        long size
) {
}
