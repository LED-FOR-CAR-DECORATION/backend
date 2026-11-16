package com.lumintra.backend.shared.exception.commission;

public class DuplicateCommissionRuleException extends RuntimeException {
    public DuplicateCommissionRuleException(String message) {
        super(message);
    }
}
