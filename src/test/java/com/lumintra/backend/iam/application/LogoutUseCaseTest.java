package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.request.LogoutRequest;
import com.lumintra.backend.iam.domain.service.RefreshTokenService;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutUseCaseTest {

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenBlacklistService blacklistService;

    @InjectMocks
    private LogoutUseCase logoutUseCase;

    @Test
    @DisplayName("UTCID01 [N]: execute blacklists access token and revokes refresh tokens for valid session")
    void execute_successfulLogoutBlacklistsTokenAndRevokesSessions() {
        String accessToken = "valid.access.jwt.token";
        LogoutRequest request = new LogoutRequest("valid.refresh.token");
        Long accountId = 100L;
        Duration remainingTtl = Duration.ofMinutes(15);

        when(tokenProvider.getAccountIdFromToken(accessToken)).thenReturn(accountId);
        when(refreshTokenService.validateAndRevoke("valid.refresh.token")).thenReturn(true);
        when(tokenProvider.getRemainingExpiration(accessToken)).thenReturn(remainingTtl);

        logoutUseCase.execute(accessToken, request);

        InOrder inOrder = inOrder(tokenProvider, refreshTokenService, blacklistService);
        inOrder.verify(tokenProvider).getAccountIdFromToken(accessToken);
        inOrder.verify(refreshTokenService).validateAndRevoke("valid.refresh.token");
        inOrder.verify(refreshTokenService).revokeAllTokensForAccount(accountId);
        inOrder.verify(tokenProvider).getRemainingExpiration(accessToken);
        inOrder.verify(blacklistService).blacklistToken(accessToken, remainingTtl);
    }

    @Test
    @DisplayName("UTCID02 [B]: execute does not blacklist token when remaining expiration is zero")
    void execute_tokenAlreadyExpiredRemainingTTLZero_doesNotCallBlacklist() {
        String accessToken = "expired.access.jwt.token";
        LogoutRequest request = new LogoutRequest("valid.refresh.token");
        Long accountId = 101L;

        when(tokenProvider.getAccountIdFromToken(accessToken)).thenReturn(accountId);
        when(tokenProvider.getRemainingExpiration(accessToken)).thenReturn(Duration.ZERO);

        logoutUseCase.execute(accessToken, request);

        verify(refreshTokenService).validateAndRevoke("valid.refresh.token");
        verify(refreshTokenService).revokeAllTokensForAccount(accountId);
        verify(blacklistService, never()).blacklistToken(anyString(), any());
    }

    @Test
    @DisplayName("UTCID03 [N]: execute continues revoking all tokens and blacklisting even if single-use validation returns false")
    void execute_refreshTokenValidationReturnsFalse_stillRevokesAllTokensAndBlacklists() {
        String accessToken = "valid.access.jwt.token";
        LogoutRequest request = new LogoutRequest("already.consumed.token");
        Long accountId = 102L;
        Duration remainingTtl = Duration.ofMinutes(10);

        when(tokenProvider.getAccountIdFromToken(accessToken)).thenReturn(accountId);
        when(refreshTokenService.validateAndRevoke("already.consumed.token")).thenReturn(false);
        when(tokenProvider.getRemainingExpiration(accessToken)).thenReturn(remainingTtl);

        logoutUseCase.execute(accessToken, request);

        verify(refreshTokenService).validateAndRevoke("already.consumed.token");
        verify(refreshTokenService).revokeAllTokensForAccount(accountId);
        verify(blacklistService).blacklistToken(accessToken, remainingTtl);
    }

    @Test
    @DisplayName("UTCID04 [A]: execute propagates exception when token provider fails to resolve account ID")
    void execute_propagatesExceptionWhenTokenProviderFails() {
        String accessToken = "malformed.access.jwt.token";
        LogoutRequest request = new LogoutRequest("some.refresh.token");

        when(tokenProvider.getAccountIdFromToken(accessToken))
                .thenThrow(new IllegalArgumentException("Invalid JWT token claim"));

        assertThatThrownBy(() -> logoutUseCase.execute(accessToken, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid JWT token claim");

        verify(refreshTokenService, never()).validateAndRevoke(anyString());
        verify(refreshTokenService, never()).revokeAllTokensForAccount(anyLong());
        verify(blacklistService, never()).blacklistToken(anyString(), any());
    }
}
