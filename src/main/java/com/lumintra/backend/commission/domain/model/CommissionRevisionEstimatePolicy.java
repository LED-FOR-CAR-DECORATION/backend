package com.lumintra.backend.commission.domain.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Revision estimate timing rules.
 *
 * <ul>
 *   <li>BR-FL3-13: the designer has {@link #ESTIMATE_WINDOW} to estimate an accepted revision,
 *       otherwise the system applies {@link #DEFAULT_ESTIMATE_HOURS}.</li>
 *   <li>BR-FL3-14: an estimate is {@link #MIN_ESTIMATE_HOURS}..{@link #MAX_ESTIMATE_HOURS} hours and
 *       the customer-facing deadline is the designer deadline plus {@link #CUSTOMER_BUFFER}.</li>
 * </ul>
 */
public final class CommissionRevisionEstimatePolicy {

    public static final Duration ESTIMATE_WINDOW = Duration.ofHours(24);
    public static final int DEFAULT_ESTIMATE_HOURS = 24;
    public static final int MIN_ESTIMATE_HOURS = 1;
    public static final int MAX_ESTIMATE_HOURS = 72;
    public static final Duration CUSTOMER_BUFFER = Duration.ofHours(24);

    private CommissionRevisionEstimatePolicy() {
    }

    public static boolean isValidEstimate(int hours) {
        return hours >= MIN_ESTIMATE_HOURS && hours <= MAX_ESTIMATE_HOURS;
    }

    public static Instant designerDueAt(Instant estimatedAt, int hours) {
        return estimatedAt.plus(Duration.ofHours(hours));
    }

    public static Instant customerDueAt(Instant designerDueAt) {
        return designerDueAt == null ? null : designerDueAt.plus(CUSTOMER_BUFFER);
    }
}
