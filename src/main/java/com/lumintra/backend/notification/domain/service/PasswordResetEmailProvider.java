package com.lumintra.backend.notification.domain.service;
public interface PasswordResetEmailProvider {
    void sendPasswordResetOtp(String email, String otp, String deliveryId);
}
