package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LogoutRequest;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(LogoutUseCase.class);
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService blacklistService;

    @Transactional
    public void execute(String accessToken, LogoutRequest request) {
        Long accountId = tokenProvider.getAccountIdFromToken(accessToken);

        refreshTokenService.validateAndRevoke(request.refreshToken());

        refreshTokenService.revokeAllTokensForAccount(accountId);

        Duration remainingTTL = tokenProvider.getRemainingExpiration(accessToken);
        if (!remainingTTL.isZero()) {
            blacklistService.blacklistToken(accessToken, remainingTTL);
        }

        log.info("User logged out of all devices successfully [accountId={}]", accountId);
    }
}