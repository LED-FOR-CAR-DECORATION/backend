package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Manager view of a warranty claim enriched with customer contact details and device metadata")
public record ManagedWarrantyClaimResponse(
        @Schema(description = "Unique ID of the warranty claim ticket", example = "1")
        Long ticketId,

        @Schema(description = "Customer account ID of the claim owner", example = "5")
        Long customerId,

        @Schema(description = "Customer full name", example = "Nguyen Van A", nullable = true)
        String customerName,

        @Schema(description = "Customer email address", example = "customer@lumintra.com", nullable = true)
        String customerEmail,

        @Schema(description = "Customer contact phone number", example = "0987654321", nullable = true)
        String customerPhone,

        @Schema(description = "Serial number of the claimed LED device", example = "LUM-6432-RGB-2026-0001")
        String deviceSerialNumber,

        @Schema(description = "Assigned user-friendly name of the LED device", example = "Living Room Display", nullable = true)
        String deviceName,

        @Schema(description = "Originating order item ID if purchased via store", example = "12", nullable = true)
        Long orderItemId,

        @Schema(description = "Current lifecycle status of the claim", example = "PENDING")
        WarrantyTicketStatus status,

        @Schema(description = "Detailed description of the hardware defect", example = "Màn hình LED bị chớp nháy sau 15 phút bật.")
        String issueDescription,

        @Schema(description = "Evidence image/video URLs supporting the claim")
        List<String> evidenceMediaUrls,

        @Schema(description = "Resolution notes entered during inspection or processing", example = "Replaced controller board", nullable = true)
        String resolutionNotes,

        @Schema(description = "Claim creation timestamp (UTC)", example = "2026-11-16T15:40:00Z")
        Instant createdAt,

        @Schema(description = "Claim last update timestamp (UTC)", example = "2026-11-16T16:00:00Z", nullable = true)
        Instant updatedAt
) {}
