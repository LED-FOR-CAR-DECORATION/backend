package com.lumintra.backend.shared.exception.commission;

public class CommissionRuleNotFoundException extends RuntimeException {
    public CommissionRuleNotFoundException(String message) {
        super(message);
    }
}
