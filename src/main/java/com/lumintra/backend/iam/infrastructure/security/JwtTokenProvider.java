package com.lumintra.backend.iam.infrastructure.security;

import com.lumintra.backend.iam.infrastructure.persistence.entity.PermissionEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.RoleEntity;
import com.lumintra.backend.iam.infrastructure.persistence.entity.UserEntity;
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
import java.util.Set;
import java.util.stream.Collectors;

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

        List<String> permissions = claims.get("permissions", List.class);
        List<SimpleGrantedAuthority> authorities = permissions != null
                ? permissions.stream().map(SimpleGrantedAuthority::new).toList()
                : List.of();

        return new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
    }

    public boolean validateToken(String token) {
        try {
            // 3. Updated validation syntax
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    } // 4. Added the missing closing brace here!

    public String createAccessToken(UserEntity user) {
        Instant now = Instant.now();
        Instant validity = now.plus(15, ChronoUnit.MINUTES); // AC01: 15-minute token

        // Extract Roles
        List<String> roles = user.getRoles().stream()
                .map(RoleEntity::getRoleName)
                .map(role -> "ROLE_" + role.toUpperCase())
                .toList();

        // Flatten Permissions from all assigned roles
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getPermissionName)
                .collect(Collectors.toSet());

        // 5. Removed deprecated SignatureAlgorithm enum; JJWT now auto-detects the algorithm from the SecretKey
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("permissions", permissions)
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken(UserEntity user) {
        Instant now = Instant.now();
        Instant validity = now.plus(30, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(validity))
                .signWith(key)
                .compact();
    }
}