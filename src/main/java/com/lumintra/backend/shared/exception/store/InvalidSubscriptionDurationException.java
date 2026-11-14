package com.lumintra.backend.shared.exception.store;

public class InvalidSubscriptionDurationException extends RuntimeException {
    public InvalidSubscriptionDurationException() {
        super(
                "Provide a positive duration for a fixed-term plan and omit duration for a lifetime"
                    + " plan.");
    }
}
