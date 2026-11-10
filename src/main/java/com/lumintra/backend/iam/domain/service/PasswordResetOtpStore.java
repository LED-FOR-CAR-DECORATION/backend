package com.lumintra.backend.iam.domain.service;

public interface PasswordResetOtpStore {
    // A null OTP counts an unknown-email request without creating a challenge.
    void issue(String email, String otp);

    // Validates the challenge and atomically consumes it; returns true if valid.
    boolean validateAndConsume(String email, String otp);
}
