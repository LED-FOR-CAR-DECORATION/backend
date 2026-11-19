package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Lifecycle of a customer revision brief ({@code COMMISSION_REVISION_REQUEST}).
 *
 * <pre>
 * PENDING ──confirm (free)──────────────────────────────► CONFIRMED ──estimate──► IN_PROGRESS
 *    │    ──confirm (paid)──► AWAITING_PAYMENT ──paid──►     │                         │
 *    │                              └──cancel/expire──► PENDING                         ▼
 *    └──newer card posted──► SUPERSEDED               new preview delivered ──► COMPLETED
 * </pre>
 *
 * <p>A confirmed brief goes straight to the assigned designer (commission {@code AWAITING_ESTIMATE});
 * there is no separate staff dispatch step.</p>
 */
@Schema(description = "Lifecycle status of a customer revision request (revision brief)")
public enum CommissionRevisionRequestStatus {
    /** Brief posted by staff, waiting for the customer to confirm it. */
    PENDING,
    /** Customer confirmed a paid revision; extra fee checkout is open. */
    AWAITING_PAYMENT,
    /** Brief is agreed (free quota or fee paid); waiting for the designer's estimate. */
    CONFIRMED,
    /** Designer estimated delivery and is working on the revision. */
    IN_PROGRESS,
    /** A preview answering this revision was delivered to the customer. */
    COMPLETED,
    /** Replaced by a newer brief card before the customer confirmed it. */
    SUPERSEDED;

    /** Statuses in which the designer must act on the brief. */
    public static List<CommissionRevisionRequestStatus> designerActiveStatuses() {
        return List.of(CONFIRMED, IN_PROGRESS);
    }
}
