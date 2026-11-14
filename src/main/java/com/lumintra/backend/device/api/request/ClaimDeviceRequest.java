package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for claiming and permanently binding a physical LED device via factory QR scan")
public record ClaimDeviceRequest(
        @Schema(
                description = "Compact HMAC-SHA256 signed factory QR JSON payload string scanned from the hardware unit",
                example = "{\"v\":1,\"sn\":\"LMT-32X64-SEED-0003\",\"mac\":\"AA:BB:CC:11:22:03\",\"sig\":\"7f8c...\"}",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "qrPayload is required")
        String qrPayload,

        @Schema(
                description = "Optional custom display name for the claimed device; defaults to the LED model name when omitted or blank",
                example = "Front Windshield LED",
                nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 255, message = "deviceName must not exceed 255 characters")
        String deviceName
) {
    public ClaimDeviceRequest {
        deviceName = deviceName != null && !deviceName.isBlank() ? deviceName.strip() : null;
    }
}
