package com.lumintra.backend.shared.exception.iam;

public class MaxResendAttemptsException extends RuntimeException {
    public MaxResendAttemptsException(String message) {
        super(message);
    }
}
