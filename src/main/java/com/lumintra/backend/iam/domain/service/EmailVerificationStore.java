package com.lumintra.backend.iam.domain.service;

public interface EmailVerificationStore {
    void issue(String email, String digest);
    boolean consume(String email, String digest);
    void invalidate(String email, String digest);
}
