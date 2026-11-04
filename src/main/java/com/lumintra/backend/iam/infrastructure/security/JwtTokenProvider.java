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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final SecretKey key;

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
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String createAccessToken(Long accountId, String email, List<String> roles) {
        Instant now = Instant.now();
        Instant validity = now.plus(15, ChronoUnit.MINUTES);

        return Jwts.builder()
                .subject(accountId.toString())
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
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(key)
                .compact();
    }
}
