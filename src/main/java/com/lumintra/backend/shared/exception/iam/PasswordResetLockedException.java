package com.lumintra.backend.shared.exception.iam;
public class PasswordResetLockedException extends RuntimeException {
    public PasswordResetLockedException() { super("Password reset requests are locked for 24 hours."); }
    public PasswordResetLockedException(Throwable cause) { super("Password reset requests are locked for 24 hours.", cause); }
}
