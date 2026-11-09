package com.lumintra.backend.shared.exception.iam;

public class ResendCooldownException extends RuntimeException {
    public ResendCooldownException(String message) {
        super(message);
    }
}
