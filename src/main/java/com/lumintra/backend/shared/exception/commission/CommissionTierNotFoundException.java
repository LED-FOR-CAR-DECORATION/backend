package com.lumintra.backend.shared.exception.commission;

public class CommissionTierNotFoundException extends RuntimeException {
    public CommissionTierNotFoundException(String message) {
        super(message);
    }
}
