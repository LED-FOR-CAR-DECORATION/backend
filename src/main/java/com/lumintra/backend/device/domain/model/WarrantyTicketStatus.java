package com.lumintra.backend.device.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle status of a hardware warranty ticket")
public enum WarrantyTicketStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED;

    public boolean canTransitionTo(WarrantyTicketStatus targetStatus) {
        if (targetStatus == null) {
            return false;
        }
        return switch (this) {
            case PENDING -> targetStatus == APPROVED || targetStatus == REJECTED;
            case APPROVED -> targetStatus == COMPLETED || targetStatus == REJECTED;
            case REJECTED, COMPLETED -> false;
        };
    }
}
