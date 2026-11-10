package com.lumintra.backend.iam.domain.event;

public record StaffPasswordChangedEvent(Long userId, String email, String role) {}
