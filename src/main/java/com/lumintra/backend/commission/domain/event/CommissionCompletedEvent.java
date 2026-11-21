package com.lumintra.backend.commission.domain.event;

import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;

import java.time.Instant;

/**
 * Domain event published when a commission transitions to COMPLETED.
 *
 * <p>Published inside the completing transaction; consumers that must only react to
 * committed state should use {@code @TransactionalEventListener(phase = AFTER_COMMIT)}.</p>
 */
public record CommissionCompletedEvent(
        Long commissionId,
        Long customerId,
        Long approvedSubmissionId,
        Long assignedStaffId,
        Long assignedDesignerId,
        CommissionCompletionSource source,
        Instant completedAt
) {}
