package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Lifecycle status of a Designer's artwork draft submission ({@code COMMISSION_SUBMISSION}).
 *
 * <p>Unapproved internal drafts ({@link #PENDING_STAFF_REVIEW} and {@link #STAFF_REJECTED})
 * are strictly internal to Staff/Designer/Manager workflows and must never be exposed
 * to the Customer ({@code AC03}).</p>
 */
@Schema(description = "Lifecycle status of a Designer commission artwork submission")
public enum CommissionSubmissionStatus {
    PENDING_STAFF_REVIEW,
    STAFF_REJECTED,
    STAFF_APPROVED,
    CUSTOMER_ACCEPTED;

    /**
     * Returns {@code true} only when the submission has passed Staff internal QA
     * and been delivered to the customer ({@link #STAFF_APPROVED} or {@link #CUSTOMER_ACCEPTED}).
     */
    public boolean isCustomerVisible() {
        return this == STAFF_APPROVED || this == CUSTOMER_ACCEPTED;
    }

    /**
     * Returns the immutable list of submission statuses that are safe to expose to customers.
     */
    public static List<CommissionSubmissionStatus> customerVisibleStatuses() {
        return List.of(STAFF_APPROVED, CUSTOMER_ACCEPTED);
    }
}
