package com.lumintra.backend.shared.exception.store;

public class SubscriptionNotFoundException extends RuntimeException {
    public SubscriptionNotFoundException() {
        super("Subscription plan not found.");
    }
}
