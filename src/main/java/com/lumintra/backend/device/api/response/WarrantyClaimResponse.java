package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;

import java.time.Instant;
import java.util.List;

public record WarrantyClaimResponse(
        Long ticketId,
        Long deviceId,
        String deviceSerialNumber,
        String deviceName,
        WarrantyTicketStatus status,
        String issueDescription,
        List<String> evidenceMediaUrls,
        String resolutionNotes,
        Instant createdAt
) {}
