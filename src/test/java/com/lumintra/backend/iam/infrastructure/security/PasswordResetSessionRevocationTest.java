package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class PasswordResetSessionRevocationTest {
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final JwtTokenProvider provider = new JwtTokenProvider(
            "this-is-a-test-secret-at-least-256-bits-long-for-unit-tests-only", accounts);

    @Test void passwordChangeRevokesEveryOldAccessAndRefreshToken() {
        UserAccountEntity account = new UserAccountEntity();
        account.setPasswordHash("old-bcrypt-hash");
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
        String access = provider.createAccessToken(1L, "test@example.com", List.of("ROLE_CUSTOMER"));
        String refresh = provider.createRefreshToken(1L);
        assertThat(provider.validateToken(access)).isTrue();
        assertThat(provider.validateToken(refresh)).isTrue();
        account.setPasswordHash("new-bcrypt-hash");
        assertThat(provider.validateToken(access)).isFalse();
        assertThat(provider.validateToken(refresh)).isFalse();
        assertThat(provider.validateToken(provider.createAccessToken(1L, "test@example.com", List.of()))).isTrue();
        assertThat(provider.validateToken(provider.createRefreshToken(1L))).isTrue();
    }

    @Test void repeatedIssuanceHasDistinctTokensEvenWithinTheSameSecond() {
        when(accounts.findById(1L)).thenReturn(Optional.of(new UserAccountEntity()));
        assertThat(provider.createRefreshToken(1L)).isNotEqualTo(provider.createRefreshToken(1L));
    }

    @Test void deletedAccountAndDatabaseOutageFailClosed() {
        when(accounts.findById(1L)).thenReturn(Optional.of(new UserAccountEntity()));
        String token = provider.createRefreshToken(1L);
        when(accounts.findById(1L)).thenReturn(Optional.empty());
        assertThat(provider.validateToken(token)).isFalse();
        when(accounts.findById(1L)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test void legacyTokensWithoutCredentialVersionRequireLoginAgain() {
        String token = new JwtTokenProvider("this-is-a-test-secret-at-least-256-bits-long-for-unit-tests-only")
                .createRefreshToken(1L);
        when(accounts.findById(1L)).thenReturn(Optional.of(new UserAccountEntity()));
        assertThat(provider.validateToken(token)).isFalse();
    }
}
