package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.notification.domain.service.EmailProvider;
import com.lumintra.backend.shared.exception.shared.ExternalProviderUnavailableException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ResendEmailClient implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);
    private final Resend resendClient;
    private final String fromEmail;

    public ResendEmailClient(
            @Value("${resend.api.key}") String apiKey,
            @Value("${resend.from.email}") String fromEmail) {
        this.resendClient = new Resend(apiKey);
        this.fromEmail = fromEmail;
    }
// EXAMPLE IMPLEMENTATION
//    @Override
//    public void sendWelcomeEmail(String emailAddress, String fullName) {
//        CreateEmailOptions params = CreateEmailOptions.builder()
//                .from(fromEmail)
//                .to(emailAddress)
//                .subject("Welcome to our platform!")
//                .html("<p>Hi " + fullName + ", welcome aboard!</p>")
//                .build();
//
//        try {
//            long start = System.currentTimeMillis();
//            CreateEmailResponse response = resendClient.emails().send(params);
//            long duration = System.currentTimeMillis() - start;
//
//            // Rule 10.1: Static human-readable prefix + structured [key=value] metadata
//            log.info("Email sent successfully [emailAddress={}] [resendId={}] [duration={}ms]",
//                    emailAddress, response.getId(), duration);
//
//        } catch (ResendException exception) {
//            // Rule 10.3: Log failure with exception object to preserve the stack trace for debugging
//            // Rule 10.4: Do not log authorization headers, credentials, or full payloads
//            log.error("Resend provider failed [emailAddress={}]", emailAddress, exception);
//
//            // Rule 6.1 & 6.3: Translate the provider exception to a typed domain exception and preserve original cause
//            throw new ExternalProviderUnavailableException(
//                    "Email provider is temporarily unavailable",
//                    exception
//            );
//        }
//    }
}
