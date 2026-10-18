package com.lumintra.backend.iam.domain.event;

public record StaffLoginSuccessEvent(Long userId, String email, String role) {}
