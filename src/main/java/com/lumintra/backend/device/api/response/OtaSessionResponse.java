package com.lumintra.backend.device.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "One-time OTA download session; the app passes the token to the device over BLE (ota_start)")
@Builder
public record OtaSessionResponse(
        @Schema(description = "Session id", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        Long sessionId,

        @Schema(description = "Single-use download token, 64 lowercase hex characters. Shown only in this response.",
                example = "4f1c9b0e2a7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c5b4a3928170f6e5d4c",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String token,

        @Schema(description = "When the token stops working (UTC), 10 minutes after issue", example = "2026-11-23T10:10:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant expiresAt,

        @Schema(description = "Firmware the device will download", requiredMode = Schema.RequiredMode.REQUIRED)
        SessionFirmware firmware
) {
    /** Keep the token out of logs and debugger dumps. */
    @Override
    public String toString() {
        return "OtaSessionResponse[sessionId=" + sessionId + ", token=<redacted>, expiresAt=" + expiresAt
                + ", firmware=" + firmware + "]";
    }

    @Schema(description = "Firmware release bound to the session")
    @Builder
    public record SessionFirmware(
            @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED) Long firmwareId,
            @Schema(example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED) String version,
            @Schema(example = "1048576", requiredMode = Schema.RequiredMode.REQUIRED) long fileSizeBytes,
            @Schema(description = "Lowercase hex SHA-256 the device verifies", requiredMode = Schema.RequiredMode.REQUIRED)
            String sha256
    ) {
    }
}
