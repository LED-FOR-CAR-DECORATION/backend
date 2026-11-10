package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.notification.domain.service.InternalUserEmailProvider;
import com.lumintra.backend.shared.exception.notification.InternalUserEmailUnavailableException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.time.Duration;
import java.util.List;

@Component
public class InternalUserEmailClient implements InternalUserEmailProvider {
    private final RestClient client;
    private final String sender;

    public InternalUserEmailClient(
            @Value("${resend.api.key}") String key, @Value("${resend.from.email}") String sender) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.client =
                RestClient.builder()
                        .baseUrl("https://api.resend.com")
                        .defaultHeader("Authorization", "Bearer " + key)
                        .requestFactory(factory)
                        .build();
        this.sender = sender;
    }

    InternalUserEmailClient(RestClient client, String sender) {
        this.client = client;
        this.sender = sender;
    }

    record Mail(String from, List<String> to, String subject, String html) {}

    @Override
    public void sendCredentials(String email, String password, String deliveryId) {
        Mail mail =
                new Mail(
                        sender,
                        List.of(email),
                        "Your Lumintra internal account",
                        "<p>Your Lumintra internal account is ready.</p><p>Your generated password"
                                + " is <strong>"
                                + org.springframework.web.util.HtmlUtils.htmlEscape(password)
                                + "</strong>.</p><p>Keep this password private.</p>");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                client.post()
                        .uri("/emails")
                        .header("Idempotency-Key", deliveryId)
                        .body(mail)
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException ex) {
                boolean retryable =
                        ex instanceof ResourceAccessException
                                || ex instanceof RestClientResponseException response
                                        && (response.getStatusCode().value() == 429
                                                || response.getStatusCode().is5xxServerError());
                if (!retryable || attempt == 1) throw new InternalUserEmailUnavailableException(ex);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new InternalUserEmailUnavailableException(interrupted);
                }
            }
        }
    }
}
