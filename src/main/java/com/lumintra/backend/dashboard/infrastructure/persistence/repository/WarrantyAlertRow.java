package com.lumintra.backend.dashboard.infrastructure.persistence.repository;

import java.time.Instant;

public record WarrantyAlertRow(
        Long ticketId,
        String customerName,
        String customerEmail,
        String deviceModel,
        String deviceSerial,
        String issueDescription,
        Instant reportedAt,
        String status
) {}
