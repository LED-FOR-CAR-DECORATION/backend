package com.lumintra.backend.shared.exception.iam;

public class MaxOtpAttemptsException extends RuntimeException {
    public MaxOtpAttemptsException(String message) {
        super(message);
    }
}
