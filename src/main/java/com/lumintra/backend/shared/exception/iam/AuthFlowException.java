package com.lumintra.backend.shared.exception.iam;

public class AuthFlowException extends RuntimeException {
    private final String code;
    public AuthFlowException(String code, String message) { this(code, message, null); }
    public AuthFlowException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
    public String getCode() { return code; }
}
