package com.lumintra.backend.iam.domain.event;
public record PasswordResetRequestedEvent(String email, String otp, String deliveryId) {
    @Override public String toString() { return "PasswordResetRequestedEvent[redacted]"; }
}
