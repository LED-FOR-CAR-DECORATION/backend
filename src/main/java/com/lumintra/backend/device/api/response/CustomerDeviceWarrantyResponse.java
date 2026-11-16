package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Response containing device warranty coverage details and ticket history")
public record CustomerDeviceWarrantyResponse(
        @Schema(description = "LED device instance ID", example = "10")
        Long deviceId,

        @Schema(description = "Device hardware serial number", example = "LUM-6432-RGB-2026-0001")
        String serialNumber,

        @Schema(description = "Device display or custom name", example = "Xe Wave Alpha - Đồng hồ LED")
        String deviceName,

        @Schema(description = "Timestamp when the device was bound/claimed", example = "2026-10-01T10:00:00Z")
        Instant boundAt,

        @Schema(description = "Timestamp when the hardware warranty expires", example = "2027-03-30T10:00:00Z")
        Instant warrantyExpiresAt,

        @Schema(description = "Current derived warranty coverage state", example = "ACTIVE")
        WarrantyStatus warrantyStatus,

        @Schema(description = "Remaining warranty days (0 if expired or not activated)", example = "134")
        Long remainingDays,

        @Schema(description = "Active warranty ticket under review or repair, if any")
        WarrantyTicketSummaryResponse activeTicket,

        @Schema(description = "Historical list of warranty tickets filed for this device")
        List<WarrantyTicketSummaryResponse> ticketHistory
) {

    @Schema(description = "Warranty ticket summary details")
    public record WarrantyTicketSummaryResponse(
            @Schema(description = "Warranty ticket ID", example = "1")
            Long ticketId,

            @Schema(description = "Ticket processing status", example = "APPROVED")
            WarrantyTicketStatus status,

            @Schema(description = "Issue description reported by customer", example = "Màn hình LED bị chớp nháy sau 15 phút.")
            String issueDescription,

            @Schema(description = "Evidence media URLs uploaded by customer")
            List<String> evidenceMediaUrls,

            @Schema(description = "Manager resolution or feedback notes", example = "Yêu cầu hợp lệ. Vui lòng mang thiết bị đến cửa hàng để được kiểm tra.")
            String resolutionNotes,

            @Schema(description = "Ticket creation timestamp", example = "2026-11-16T10:00:00Z")
            Instant createdAt,

            @Schema(description = "Ticket last update timestamp", example = "2026-11-16T14:30:00Z")
            Instant updatedAt
    ) {}
}
