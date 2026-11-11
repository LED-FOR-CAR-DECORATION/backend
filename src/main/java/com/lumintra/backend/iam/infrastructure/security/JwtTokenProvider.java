package com.lumintra.backend.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository accounts;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
            com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository accounts) {
        this(secret);
        this.accounts = accounts;
    }

    private String credentialVersion(Long accountId) {
        if (accounts == null) return "unit-test";
        var account = accounts.findById(accountId).orElseThrow(() ->
                new com.lumintra.backend.shared.exception.iam.InvalidCredentialsException("Account not found."));
        if (account.isAccessDisabled()) {
            throw new com.lumintra.backend.shared.exception.iam.InvalidCredentialsException("Account unavailable.");
        }
        String version = accountId + ":" + account.getPasswordHash();
        if (account.getUpdatedAt() != null) {
            version += ":" + account.getUpdatedAt().truncatedTo(ChronoUnit.MICROS);
        }
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(key);
            return java.util.HexFormat.of().formatHex(mac.doFinal(
                    version.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Credential verification unavailable", ex);
        }
    }

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {
        // In production, this must be a strong 256-bit + key from the environment
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Authentication getAuthentication(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        List<String> roles = claims.get("roles", List.class);
        List<SimpleGrantedAuthority> authorities = roles != null
                ? roles.stream().map(SimpleGrantedAuthority::new).toList()
                : List.of();

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }

    public boolean validateToken(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (accounts == null) return true;
            String actual = claims.get("credentialVersion", String.class);
            return actual != null && java.security.MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.UTF_8),
                    credentialVersion(Long.parseLong(claims.getSubject())).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }

    public String createAccessToken(Long accountId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant validity = now.plus(15, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(accountId.toString())
                .id(java.util.UUID.randomUUID().toString())
                .claim("credentialVersion", credentialVersion(accountId))
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(key)
                .compact();
    }

    public Long getAccountIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public String createRefreshToken(Long accountId) {
        Instant now = Instant.now();
        Instant validity = now.plus(30, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(accountId.toString())
                .id(java.util.UUID.randomUUID().toString())
                .claim("credentialVersion", credentialVersion(accountId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(key)
                .compact();
    }

    public Duration getRemainingExpiration(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            long diff = claims.getExpiration().getTime() - System.currentTimeMillis();
            return diff > 0 ? Duration.ofMillis(diff) : Duration.ZERO;
        } catch (Exception ex) {
            return Duration.ZERO;
        }
    }
}
