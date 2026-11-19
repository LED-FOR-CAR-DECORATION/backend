package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Lifecycle states of a custom artwork commission request")
public enum CommissionStatus {
    PENDING_REVIEW,
    PENDING_PAYMENT,
    PAID,
    IN_PROGRESS,
    INTERNAL_REVIEW,
    INTERNAL_REVISION_REQUIRED,
    PREVIEW_DELIVERED,
    AWAITING_EXTRA_PAYMENT,
    AWAITING_ESTIMATE,
    COMPLETED,
    CANCELLED,
    EXPIRED;

    public boolean isPaymentPending() {
        return this == PENDING_PAYMENT || this == AWAITING_EXTRA_PAYMENT;
    }

    public boolean isChatActive() {
        return switch (this) {
            case PAID,
                 IN_PROGRESS,
                 INTERNAL_REVIEW,
                 INTERNAL_REVISION_REQUIRED,
                 PREVIEW_DELIVERED,
                 AWAITING_EXTRA_PAYMENT,
                 AWAITING_ESTIMATE -> true;
            default -> false;
        };
    }
}
