package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TokenSessionService {
    private final UserAccountRepository accounts;
    private final AuthSessionRepository sessions;
    private final EmployeeProfileRepository employees;
    private final JwtTokenProvider tokens;

    public static void checkAccount(UserAccountEntity account, boolean management) {
        if (account.getLockoutUntil() != null && account.getLockoutUntil().isAfter(Instant.now())) {
            throw new com.lumintra.backend.shared.exception.iam.AccountLockedException("Account is locked for 15 minutes.");
        }
        if (!"ACTIVE".equals(account.getStatus())) throw new AuthFlowException("ACCOUNT_DISABLED", "The account is inactive.");
        if (management && account.getAccountType() != AccountType.EMPLOYEE) {
            throw new AuthFlowException("MANAGEMENT_ACCESS_DENIED", "The account cannot access the management application.");
        }
    }

    @Transactional
    public LoginResponse issue(Long accountId, boolean management) {
        var account = accounts.lockById(accountId).orElseThrow(TokenSessionService::invalidToken);
        checkAccount(account, management);
        AuthSessionEntity session = new AuthSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setAccount(account);
        session.setManagement(management);
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        LoginResponse response = rotateTokens(account, session);
        sessions.saveAndFlush(session);
        return response;
    }

    @Transactional
    public LoginResponse refresh(String refreshToken) {
        var claims = tokens.parseRefreshToken(refreshToken);
        // All issue/refresh/logout operations serialize on the account, in the same lock order.
        var account = accounts.lockById(Long.valueOf(claims.getSubject())).orElseThrow(TokenSessionService::invalidToken);
        var session = sessions.findById(claims.get("sid", String.class)).orElseThrow(TokenSessionService::invalidToken);
        if (!session.getAccount().getAccountId().equals(account.getAccountId()) || session.isRevoked()
                || !session.getExpiresAt().isAfter(Instant.now())
                || !MessageDigest.isEqual(session.getRefreshHash().getBytes(StandardCharsets.US_ASCII), hash(refreshToken).getBytes(StandardCharsets.US_ASCII))) {
            throw invalidToken();
        }
        checkAccount(account, session.isManagement());
        var response = rotateTokens(account, session);
        sessions.saveAndFlush(session);
        return response;
    }

    private LoginResponse rotateTokens(UserAccountEntity account, AuthSessionEntity session) {
        List<String> roles = List.of("ROLE_CUSTOMER");
        Set<String> permissions = Set.of();
        if (account.getAccountType() == AccountType.EMPLOYEE) {
            var employee = employees.findByAccount_AccountId(account.getAccountId())
                    .orElseThrow(() -> new IllegalStateException("Employee profile missing"));
            roles = List.of("ROLE_" + employee.getRole().getRoleName().toUpperCase(Locale.ROOT));
            permissions = employee.getRole().getPermissions().stream().map(PermissionEntity::getPermissionName).collect(Collectors.toSet());
        }
        String refresh = tokens.createRefreshToken(account.getAccountId(), session.getId(), session.getExpiresAt());
        session.setRefreshHash(hash(refresh));
        return LoginResponse.builder().accessToken(tokens.createAccessToken(account.getAccountId(), account.getEmail(), roles, permissions, session.getId()))
                .refreshToken(refresh).tokenType("Bearer").expiresIn(900).build();
    }

    @Transactional(readOnly = true)
    public Authentication authenticate(String accessToken) {
        var claims = tokens.parseAccessToken(accessToken);
        Long accountId = Long.valueOf(claims.getSubject());
        var account = accounts.findById(accountId).orElseThrow(TokenSessionService::invalidToken);
        checkAccount(account, false);
        if (!sessions.existsByIdAndAccount_AccountIdAndRevokedFalseAndExpiresAtAfter(claims.get("sid", String.class), accountId, Instant.now())) throw invalidToken();
        return tokens.getAuthentication(accessToken);
    }

    @Transactional
    public void revokeAll(String accessToken) {
        var claims = tokens.parseAccessToken(accessToken);
        Long accountId = Long.valueOf(claims.getSubject());
        accounts.lockById(accountId).orElseThrow(TokenSessionService::invalidToken);
        if (!sessions.existsByIdAndAccount_AccountIdAndRevokedFalseAndExpiresAtAfter(claims.get("sid", String.class), accountId, Instant.now())) throw invalidToken();
        sessions.revokeAll(accountId);
    }

    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    private static AuthFlowException invalidToken() { return new AuthFlowException("INVALID_TOKEN", "The token is invalid, expired or revoked."); }
}
