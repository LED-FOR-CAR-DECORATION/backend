package com.lumintra.backend.commission.domain.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How a commission reached {@link CommissionStatus#COMPLETED}.
 */
@Schema(description = "Source that finalized a commission")
public enum CommissionCompletionSource {
    /** Customer explicitly approved the Deliverable Card (CMS-18, BR-FL3-06). */
    CUSTOMER_APPROVED,
    /** Auto-completed after the review window elapsed without customer action. */
    AUTO_COMPLETED
}
