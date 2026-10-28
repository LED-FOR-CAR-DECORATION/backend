package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import io.jsonwebtoken.*;
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
import java.util.*;

@Component
public class JwtTokenProvider {
    private final SecretKey key;
    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    private Claims parse(String token, String type) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            if (!type.equals(claims.get("type")) || claims.getExpiration() == null
                    || claims.getId() == null || claims.get("sid", String.class) == null || claims.getSubject() == null) {
                throw new IllegalArgumentException("Missing token claims");
            }
            Long.parseLong(claims.getSubject());
            Long.parseLong(claims.get("sid",String.class));
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            throw new AuthFlowException("INVALID_TOKEN", "The token is invalid or expired.", ex);
        }
    }
    public Claims parseAccessToken(String token) { return parse(token, "access"); }
    public Claims parseRefreshToken(String token) { return parse(token, "refresh"); }
    public boolean validateToken(String token) {
        try { parseAccessToken(token); return true; } catch (AuthFlowException ex) { return false; }
    }
    public Authentication getAuthentication(String token) {
        Claims claims = parseAccessToken(token);
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        for (String field : List.of("roles", "permissions")) {
            Object values = claims.get(field);
            if (values instanceof List<?> list) for (Object value : list) {
                if (value instanceof String text) authorities.add(new SimpleGrantedAuthority(text));
            }
        }
        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }
    public String createAccessToken(Long accountId, String email, List<String> roles, Set<String> permissions, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(accountId.toString())
                .claim("type", "access").claim("sid", sessionId).claim("email", email)
                .claim("roles", roles).claim("permissions", permissions).issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(15, ChronoUnit.MINUTES))).signWith(key).compact();
    }
    public String createRefreshToken(Long accountId, String sessionId, Instant expiresAt) {
        return Jwts.builder().id(UUID.randomUUID().toString()).subject(accountId.toString())
                .claim("type", "refresh").claim("sid", sessionId).issuedAt(new Date())
                .expiration(Date.from(expiresAt)).signWith(key).compact();
    }
}
