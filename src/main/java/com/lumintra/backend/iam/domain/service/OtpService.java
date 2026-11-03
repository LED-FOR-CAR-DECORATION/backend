package com.lumintra.backend.iam.domain.service;

import com.lumintra.backend.iam.domain.model.OtpPurpose;

public interface OtpService {
    String generateAndStoreOtp(String email, OtpPurpose purpose);
    boolean validateOtp(String email, String otp, OtpPurpose purpose);
}