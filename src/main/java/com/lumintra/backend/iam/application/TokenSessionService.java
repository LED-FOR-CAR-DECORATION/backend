package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.api.response.LoginResponse;
import com.lumintra.backend.iam.domain.model.AccountType;
import com.lumintra.backend.iam.infrastructure.persistence.entity.*;
import com.lumintra.backend.iam.infrastructure.persistence.repository.*;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.iam.*;
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

@Service @RequiredArgsConstructor
public class TokenSessionService {
    private final UserAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final EmployeeProfileRepository employees;
    private final JwtTokenProvider tokens;

    public static void checkAccount(UserAccountEntity account, boolean management) {
        if ("PENDING_VERIFICATION".equals(account.getStatus())) throw new EmailVerificationException("EMAIL_NOT_VERIFIED","Verify your email before signing in.");
        if (account.getLockoutUntil()!=null && account.getLockoutUntil().isAfter(Instant.now())) throw new AccountLockedException("Account is locked for 15 minutes.");
        if (!"ACTIVE".equals(account.getStatus())) throw new AuthFlowException("ACCOUNT_DISABLED","The account is inactive.");
        if (management && account.getAccountType()!=AccountType.EMPLOYEE) throw new AuthFlowException("MANAGEMENT_ACCESS_DENIED","The account cannot access the management application.");
    }
    @Transactional
    public LoginResponse issue(Long accountId, boolean management) {
        var account=accounts.lockById(accountId).orElseThrow(TokenSessionService::invalidToken);
        checkAccount(account,management);
        var record=RefreshTokenEntity.create(account,Instant.now().plus(30,ChronoUnit.DAYS));
        refreshTokens.saveAndFlush(record);
        var response=rotateTokens(account,record);
        refreshTokens.saveAndFlush(record);
        return response;
    }
    private LoginResponse rotateTokens(UserAccountEntity account,RefreshTokenEntity record) {
        List<String> roles=List.of("ROLE_CUSTOMER"); Set<String> permissions=Set.of();
        if(account.getAccountType()==AccountType.EMPLOYEE) {
            var employee=employees.findByAccount_AccountId(account.getAccountId()).orElseThrow(()->new IllegalStateException("Employee profile missing"));
            roles=List.of("ROLE_"+employee.getRole().getRoleName().toUpperCase(Locale.ROOT));
            permissions=employee.getRole().getPermissions().stream().map(PermissionEntity::getPermissionName).collect(Collectors.toSet());
        }
        String id=record.getRefreshTokenId().toString();
        String refresh=tokens.createRefreshToken(account.getAccountId(),id,record.getExpiresAt());
        record.setTokenHash(hash(refresh));
        return LoginResponse.builder().accessToken(tokens.createAccessToken(account.getAccountId(),account.getEmail(),roles,permissions,id))
                .refreshToken(refresh).tokenType("Bearer").expiresIn(900).build();
    }
    @Transactional(readOnly=true)
    public Authentication authenticate(String token) {
        var claims=tokens.parseAccessToken(token); Long accountId=Long.valueOf(claims.getSubject());
        var account=accounts.findById(accountId).orElseThrow(TokenSessionService::invalidToken);
        checkAccount(account,false);
        if(!refreshTokens.existsByRefreshTokenIdAndAccount_AccountIdAndRevokedAtIsNullAndExpiresAtAfter(Long.valueOf(claims.get("sid",String.class)),accountId,Instant.now())) throw invalidToken();
        return tokens.getAuthentication(token);
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException ex){throw new IllegalStateException("SHA-256 unavailable",ex);}
    }
    private static AuthFlowException invalidToken(){return new AuthFlowException("INVALID_TOKEN","The token is invalid, expired or revoked.");}
    @Transactional
    public LoginResponse refresh(String token) {
        var claims=tokens.parseRefreshToken(token);
        var account=accounts.lockById(Long.valueOf(claims.getSubject())).orElseThrow(TokenSessionService::invalidToken);
        var record=refreshTokens.findById(Long.valueOf(claims.get("sid",String.class))).orElseThrow(TokenSessionService::invalidToken);
        if(!record.getAccount().getAccountId().equals(account.getAccountId()) || record.getRevokedAt()!=null || !record.getExpiresAt().isAfter(Instant.now())
                || !MessageDigest.isEqual(record.getTokenHash().getBytes(StandardCharsets.US_ASCII),hash(token).getBytes(StandardCharsets.US_ASCII))) throw invalidToken();
        checkAccount(account,false);
        var response=rotateTokens(account,record);
        refreshTokens.saveAndFlush(record);
        return response;
    }
    @Transactional
    public void revokeAll(String token) {
        var claims=tokens.parseAccessToken(token); Long accountId=Long.valueOf(claims.getSubject());
        accounts.lockById(accountId).orElseThrow(TokenSessionService::invalidToken);
        if(!refreshTokens.existsByRefreshTokenIdAndAccount_AccountIdAndRevokedAtIsNullAndExpiresAtAfter(Long.valueOf(claims.get("sid",String.class)),accountId,Instant.now())) throw invalidToken();
        refreshTokens.revokeAll(accountId,Instant.now());
    }

}
