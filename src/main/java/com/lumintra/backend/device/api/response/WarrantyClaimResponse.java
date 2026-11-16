package com.lumintra.backend.device.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Details of a hardware warranty claim ticket")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarrantyClaimResponse(
        @Schema(description = "Unique ticket ID", example = "1")
        Long ticketId,

        @Schema(description = "Device ID", example = "10")
        Long deviceId,

        @Schema(description = "Device hardware serial number", example = "LUM-6432-RGB-2026-0001")
        String deviceSerialNumber,

        @Schema(description = "Assigned user-friendly name of the LED device", example = "Living Room LED", nullable = true)
        String deviceName,

        @Schema(description = "Current lifecycle status of the claim", example = "APPROVED")
        WarrantyTicketStatus status,

        @Schema(description = "Description of the hardware defect", example = "Màn hình LED bị chớp nháy và mất hàng bóng số 3 sau khi bật 15 phút.")
        String issueDescription,

        @Schema(description = "Evidence image/video URLs supporting the claim")
        List<String> evidenceMediaUrls,

        @Schema(description = "Resolution or rejection notes entered by management", example = "Yêu cầu bảo hành hợp lệ.", nullable = true)
        String resolutionNotes,

        @Schema(description = "Creation timestamp (UTC)", example = "2026-11-16T15:40:00Z")
        Instant createdAt,

        @Schema(description = "Last update timestamp (UTC)", example = "2026-11-16T16:15:00Z", nullable = true)
        Instant updatedAt
) {
    public WarrantyClaimResponse(
            Long ticketId,
            Long deviceId,
            String deviceSerialNumber,
            String deviceName,
            WarrantyTicketStatus status,
            String issueDescription,
            List<String> evidenceMediaUrls,
            String resolutionNotes,
            Instant createdAt
    ) {
        this(ticketId, deviceId, deviceSerialNumber, deviceName, status, issueDescription, evidenceMediaUrls, resolutionNotes, createdAt, null);
    }
}
