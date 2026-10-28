package com.lumintra.backend.shared.exception.iam;

public class EmailVerificationException extends RuntimeException {
    private final String code;
    public EmailVerificationException(String code, String message) { this(code, message, null); }
    public EmailVerificationException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    public String getCode() { return code; }
}
