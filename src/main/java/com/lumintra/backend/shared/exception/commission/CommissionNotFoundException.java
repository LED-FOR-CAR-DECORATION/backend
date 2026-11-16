package com.lumintra.backend.shared.exception.commission;

public class CommissionNotFoundException extends RuntimeException {
    public CommissionNotFoundException(String message) {
        super(message);
    }
}
