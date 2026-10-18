package com.lumintra.backend.iam.domain.service;

public interface LoginAttemptService {
    boolean isAccountLocked(String email);
    int registerFailedAttempt(String email);
    void resetAttempts(String email);
}
