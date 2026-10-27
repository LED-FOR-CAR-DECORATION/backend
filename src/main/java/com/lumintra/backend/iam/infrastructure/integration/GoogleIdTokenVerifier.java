package com.lumintra.backend.iam.infrastructure.integration;

import com.lumintra.backend.iam.domain.service.GoogleIdentityVerifier;
import com.lumintra.backend.shared.exception.iam.AuthFlowException;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.*;

@Component
public class GoogleIdTokenVerifier implements GoogleIdentityVerifier {
    private final String clientId;
    private final JwtDecoder decoder;

    @Autowired
    public GoogleIdTokenVerifier(@Value("${google.client.id:}") String clientId) {
        this.clientId = clientId;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        var nimbus = NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(new RestTemplate(factory)).build();
        nimbus.setJwtValidator(new JwtTimestampValidator(Duration.ZERO));
        this.decoder = nimbus;
    }

    public GoogleIdTokenVerifier(String clientId, JwtDecoder decoder) { this.clientId = clientId; this.decoder = decoder; }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) throw new AuthFlowException("GOOGLE_UNAVAILABLE", "Đăng nhập Google chưa được cấu hình.");
        try {
            Jwt jwt = decodeWithRetry(idToken);
            String issuer = jwt.getClaimAsString("iss");
            String email = jwt.getClaimAsString("email");
            if (!Set.of("accounts.google.com", "https://accounts.google.com").contains(issuer == null ? "" : issuer)
                    || !jwt.getAudience().contains(clientId) || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(java.time.Instant.now())
                    || jwt.getSubject() == null || jwt.getSubject().isBlank() || jwt.getSubject().length() > 255
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                    || email == null || email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw invalid();
            email = email.toLowerCase(Locale.ROOT);
            String hostedDomain = jwt.getClaimAsString("hd");
            boolean authoritative = email.endsWith("@gmail.com") || (hostedDomain != null && !hostedDomain.isBlank());
            return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"), authoritative);
        } catch (BadJwtException ex) {
            throw new AuthFlowException("INVALID_GOOGLE_TOKEN", "Google ID token không hợp lệ hoặc đã hết hạn.", ex);
        } catch (JwtException ex) {
            throw new AuthFlowException("GOOGLE_UNAVAILABLE", "Không thể xác minh với Google. Vui lòng thử lại.", ex);
        }
    }
    private Jwt decodeWithRetry(String idToken) {
        // Retry only a transient key/provider failure; rejected tokens are never retried.
        try { return decoder.decode(idToken); }
        catch (BadJwtException ex) { throw ex; }
        catch (JwtException ex) {
            java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            if (Thread.currentThread().isInterrupted()) throw ex;
            return decoder.decode(idToken);
        }
    }
    private static AuthFlowException invalid() { return new AuthFlowException("INVALID_GOOGLE_TOKEN", "Google ID token không hợp lệ hoặc email chưa được xác minh."); }
}
