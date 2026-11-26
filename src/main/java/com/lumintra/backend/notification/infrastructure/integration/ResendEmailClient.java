package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.notification.domain.service.EmailProvider;
import com.lumintra.backend.notification.domain.service.InternalUserEmailProvider;
import com.lumintra.backend.shared.exception.notification.InternalUserEmailUnavailableException;
import com.lumintra.backend.shared.exception.shared.ExternalProviderUnavailableException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.List;

@Component
public class ResendEmailClient implements EmailProvider, InternalUserEmailProvider {
    private final RestClient client;
    private final String sender;

    @Autowired
    public ResendEmailClient(
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

    ResendEmailClient(RestClient client, String sender) {
        this.client = client;
        this.sender = sender;
    }

    record Mail(String from, List<String> to, String subject, String html) {}

    @Override
    public void sendOtpEmail(String email, String fullName, String otp) {
        String name = fullName == null || fullName.isBlank() ? "Customer" : fullName;
        var mail =
                new Mail(
                        sender,
                        List.of(email),
                        "Verify your Lumintra account",
                        "<p>Hi "
                                + HtmlUtils.htmlEscape(name)
                                + ",</p>"
                                + "<p>Your verification code is: <strong>"
                                + HtmlUtils.htmlEscape(otp)
                                + "</strong></p><p>This code expires in 15 minutes.</p>");
        try {
            send(mail, null);
        } catch (RestClientException ex) {
            throw new ExternalProviderUnavailableException(
                    "Email provider is temporarily unavailable");
        }
    }

    @Override
    public void sendCredentials(String email, String password, String deliveryId) {
        Mail mail =
                new Mail(
                        sender,
                        List.of(email),
                        "Your Lumintra internal account",
                        "<p>Your Lumintra internal account is ready.</p><p>Your generated password"
                                + " is <strong>"
                                + HtmlUtils.htmlEscape(password)
                                + "</strong>.</p><p>Keep this password private.</p>");
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                send(mail, deliveryId);
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

    private void send(Mail mail, String deliveryId) {
        var request = client.post().uri("/emails");
        if (deliveryId != null) {
            request.header("Idempotency-Key", deliveryId);
        }
        request.body(mail).retrieve().toBodilessEntity();
    }
}
