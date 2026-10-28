package com.lumintra.backend.notification.domain.service;

public interface EmailProvider {
    void sendRegistrationOtp(String emailAddress, String otp, String idempotencyKey);
}
