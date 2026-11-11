package com.lumintra.backend.iam.domain.event;

import java.time.Instant;

public record InternalUserCreatedEvent(
        Long accountId,
        Long createdBy,
        String role,
        String email,
        String password,
        String deliveryId,
        Instant createdAt) {
    @Override
    public String toString() {
        return "InternalUserCreatedEvent[accountId="
                + accountId
                + ", createdBy="
                + createdBy
                + ", role="
                + role
                + "]";
    }
}
