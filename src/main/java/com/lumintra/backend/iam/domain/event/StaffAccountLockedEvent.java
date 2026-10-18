package com.lumintra.backend.iam.domain.event;

public record StaffAccountLockedEvent(Long userId, String email, String role) {}
