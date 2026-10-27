package com.lumintra.backend.iam.application;

import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.caching.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class LogoutUseCase {
    private final TokenSessionService sessions;
    private final JwtTokenProvider tokens;
    private final SafeCacheService cache;

    public void execute(String accessToken) {
        var claims = tokens.parseAccessToken(accessToken);
        sessions.revokeAll(accessToken); // Commit before the best-effort cache write.
        Duration ttl = Duration.between(Instant.now(), claims.getExpiration().toInstant());
        if (ttl.isNegative() || ttl.isZero()) return;
        try { cache.set("auth:blacklist:" + claims.getId(), true, ttl); }
        catch (CacheUnavailableException ex) {
            LoggerFactory.getLogger(LogoutUseCase.class).warn("Access blacklist cache unavailable; database sessions revoked");
        }
    }
}
