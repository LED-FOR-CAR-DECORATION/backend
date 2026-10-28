package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.notification.domain.service.EmailProvider;
import com.lumintra.backend.shared.exception.iam.EmailVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.Duration;
import java.util.List;

@Component
public class ResendEmailClient implements EmailProvider {
    private final RestClient client;
    private final String from;
    private final boolean configured;
    @org.springframework.beans.factory.annotation.Autowired
    public ResendEmailClient(@Value("${resend.api.key:}") String apiKey, @Value("${resend.from.email:noreply@lumintra.shop}") String from) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3)); factory.setReadTimeout(Duration.ofSeconds(3));
        client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.from = from; configured = !apiKey.isBlank();
    }
    ResendEmailClient(RestClient client, String from) { this.client = client; this.from = from; this.configured = true; }
    @Override public void sendRegistrationOtp(String email, String otp, String idempotencyKey) {
        if (!configured) throw unavailable(null);
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                client.post().uri("/emails").header("Idempotency-Key", idempotencyKey)
                        .body(new EmailMessage(from, List.of(email), "Verify your Lumintra email", "Your verification code is " + otp + ". It expires in five minutes."))
                        .retrieve().toBodilessEntity();
                return;
            } catch (RestClientResponseException ex) {
                boolean retryable = ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 429;
                if (!retryable || attempt == 1) throw unavailable(ex);
            } catch (ResourceAccessException ex) { if (attempt == 1) throw unavailable(ex); }
            catch (RestClientException ex) { throw unavailable(ex); }
            java.util.concurrent.locks.LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
            if (Thread.currentThread().isInterrupted()) throw unavailable(null);
        }
    }
    private static EmailVerificationException unavailable(Throwable cause) {
        return new EmailVerificationException("VERIFICATION_UNAVAILABLE", "The verification email could not be sent. Use resend verification to retry.", cause);
    }
    private record EmailMessage(String from, List<String> to, String subject, String text) {}
}
