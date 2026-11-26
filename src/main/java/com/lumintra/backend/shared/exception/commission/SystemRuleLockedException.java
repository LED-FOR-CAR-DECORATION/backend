package com.lumintra.backend.shared.exception.commission;

public class SystemRuleLockedException extends RuntimeException {
    public SystemRuleLockedException(String message) {
        super(message);
    }
}
