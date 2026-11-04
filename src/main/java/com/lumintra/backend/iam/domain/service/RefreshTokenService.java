package com.lumintra.backend.iam.domain.service;

public interface RefreshTokenService {
    void storeRefreshToken(Long accountId, String rawToken);
    boolean validateAndRevoke(String rawToken);
}