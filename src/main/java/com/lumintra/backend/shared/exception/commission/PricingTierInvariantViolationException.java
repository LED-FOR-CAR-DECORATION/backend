package com.lumintra.backend.shared.exception.commission;

public class PricingTierInvariantViolationException extends RuntimeException {
    public PricingTierInvariantViolationException(String message) {
        super(message);
    }
}
