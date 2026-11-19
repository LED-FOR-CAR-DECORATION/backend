package com.lumintra.backend.shared.exception.commission;

/**
 * A pricing rule or option cannot be deactivated or re-linked because an active preset or another
 * active rule still references it. Mapped to 409 CONFLICT.
 */
public class PricingRuleInUseException extends RuntimeException {
    public PricingRuleInUseException(String message) {
        super(message);
    }
}
