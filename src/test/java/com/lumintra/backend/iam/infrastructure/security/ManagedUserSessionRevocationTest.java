package com.lumintra.backend.iam.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.lumintra.backend.iam.domain.model.AccountStatus;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserAccountEntity;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

class ManagedUserSessionRevocationTest {
    @Test
    @DisplayName(
            "A: An existing bearer token stops authenticating after updates and stays invalid after"
                + " unlocking")
    void existingTokenIsRejected() {
        var accounts = mock(UserAccountRepository.class);
        var account = new UserAccountEntity();
        account.setStatus(AccountStatus.ACTIVE);
        account.setPasswordHash("fixture-hash");
        when(accounts.findById(2L)).thenReturn(Optional.of(account));
        var tokens = new JwtTokenProvider("local-test-key-of-at-least-thirty-two-bytes", accounts);
        String token = tokens.createAccessToken(2L, "test@example.test", List.of("ROLE_CUSTOMER"));
        assertThat(tokens.validateToken(token)).isTrue();
        account.setUpdatedAt(Instant.now());
        assertThat(tokens.validateToken(token)).isFalse();
        String replacement =
                tokens.createAccessToken(2L, "test@example.test", List.of("ROLE_STAFF"));
        assertThat(tokens.validateToken(replacement)).isTrue();
        account.setStatus(AccountStatus.SUSPENDED);
        assertThat(tokens.validateToken(replacement)).isFalse();
        account.setUpdatedAt(account.getUpdatedAt().plusSeconds(1));
        account.setStatus(AccountStatus.ACTIVE);
        assertThat(tokens.validateToken(replacement)).isFalse();
        assertThat(tokens.validateToken(token)).isFalse();
    }
}
