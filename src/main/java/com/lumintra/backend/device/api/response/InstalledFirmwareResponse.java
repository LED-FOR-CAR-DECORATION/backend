package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

/** DEV-10B: the firmware now recorded for the device, as reported by the app. */
@Builder
@Schema(description = "Firmware recorded as installed on the device (reported by the app, not verified by the server)")
public record InstalledFirmwareResponse(
        @Schema(description = "LED device (instance) id", example = "2001")
        Long deviceId,
        @Schema(description = "Recorded firmware release id", example = "12")
        Long firmwareId,
        @Schema(description = "Recorded firmware version", example = "1.2.0")
        String version,
        @Schema(description = "Current status of that release: ACTIVE, SUPERSEDED or DEPRECATED", example = "ACTIVE")
        FirmwareStatus firmwareStatus,
        @Schema(description = "When the recorded firmware last changed", example = "2026-11-24T10:15:00Z")
        Instant firmwareUpdatedAt
) {
}
