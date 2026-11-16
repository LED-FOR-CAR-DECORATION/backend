package com.lumintra.backend.device.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle status of a hardware warranty ticket")
public enum WarrantyTicketStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED
}
