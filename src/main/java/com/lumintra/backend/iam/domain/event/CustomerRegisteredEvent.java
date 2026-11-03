package com.lumintra.backend.iam.domain.event;

public record CustomerRegisteredEvent(Long userId, String email, String fullName, String otp) {}