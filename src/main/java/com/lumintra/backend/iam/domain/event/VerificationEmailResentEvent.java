package com.lumintra.backend.iam.domain.event;

public record VerificationEmailResentEvent(String email, String otp) {
}
