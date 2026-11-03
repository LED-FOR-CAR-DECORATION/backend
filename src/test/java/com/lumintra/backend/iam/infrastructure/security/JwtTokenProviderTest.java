package com.lumintra.backend.iam.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String SECRET = "this-is-a-super-secret-key-for-testing-purposes-that-is-at-least-256-bits-long!";
    private static final String OTHER_SECRET = "another-completely-different-secret-key-for-testing-purposes-256-bits-long!";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET);
    }

    @Test
    @DisplayName("UTCID01 [N]: createAccessToken creates a valid JWT token with claims")
    void createAccessToken_validClaims() {
        Long accountId = 42L;
        String email = "worker@lumintra.com";
        List<String> roles = List.of("EMPLOYEE", "TECHNICIAN");
        Set<String> permissions = Set.of("ORDER_READ", "ORDER_UPDATE");

        String token = jwtTokenProvider.createAccessToken(accountId, email, roles, permissions);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("UTCID02 [N]: createRefreshToken creates a valid JWT refresh token")
    void createRefreshToken_validToken() {
        Long accountId = 42L;

        String refreshToken = jwtTokenProvider.createRefreshToken(accountId);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("UTCID03 [N]: validateToken returns true for valid access token")
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.createAccessToken(1L, "user@test.com", List.of("CUSTOMER"), Set.of());

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("UTCID04 [A]: validateToken returns false for malformed or tampered token")
    void validateToken_malformedToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("invalid.jwt.token")).isFalse();
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("UTCID05 [A]: validateToken returns false for token signed with a different key")
    void validateToken_differentSecretKey_returnsFalse() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(OTHER_SECRET);
        String tokenFromOther = otherProvider.createAccessToken(1L, "user@test.com", List.of("CUSTOMER"), Set.of());

        assertThat(jwtTokenProvider.validateToken(tokenFromOther)).isFalse();
    }

    @Test
    @DisplayName("UTCID06 [N]: getAuthentication returns Authentication with subject and authorities from permissions")
    void getAuthentication_validPermissions_returnsAuthentication() {
        Long accountId = 10L;
        String email = "admin@lumintra.com";
        List<String> roles = List.of("ADMIN");
        Set<String> permissions = Set.of("USER_MANAGE", "ROLE_ASSIGN");

        String token = jwtTokenProvider.createAccessToken(accountId, email, roles, permissions);
        Authentication authentication = jwtTokenProvider.getAuthentication(token);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("10");
        List<String> granted = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        assertThat(granted).containsExactlyInAnyOrder("USER_MANAGE", "ROLE_ASSIGN");
    }

    @Test
    @DisplayName("UTCID07 [B]: getAuthentication returns Authentication with empty authorities when permissions is empty")
    void getAuthentication_emptyPermissions_returnsEmptyAuthorities() {
        Long accountId = 11L;
        String email = "customer@lumintra.com";
        List<String> roles = List.of("CUSTOMER");
        Set<String> permissions = Set.of();

        String token = jwtTokenProvider.createAccessToken(accountId, email, roles, permissions);
        Authentication authentication = jwtTokenProvider.getAuthentication(token);

        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("11");
        assertThat(authentication.getAuthorities()).isEmpty();
    }
}
