package com.lumintra.backend.shared.exception.iam;

public class InvalidOtpException extends RuntimeException {
    public InvalidOtpException(String message) { super(message); }
}