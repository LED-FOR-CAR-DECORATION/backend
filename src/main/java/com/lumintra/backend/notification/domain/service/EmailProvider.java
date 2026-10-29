package com.lumintra.backend.notification.domain.service;

public interface EmailProvider {
    void sendOtpEmail(String emailAddress, String fullName, String otp);
}
