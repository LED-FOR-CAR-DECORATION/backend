package com.lumintra.backend.shared.exception.iam;
public class PasswordResetUnavailableException extends RuntimeException {
    public PasswordResetUnavailableException() { super("Password reset is temporarily unavailable."); }
    public PasswordResetUnavailableException(Throwable cause) { super("Password reset is temporarily unavailable.", cause); }
}
