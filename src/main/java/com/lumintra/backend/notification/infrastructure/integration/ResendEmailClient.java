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

    @Override
    public void sendOtpEmail(String emailAddress, String fullName, String otp) {
        String displayName = (fullName != null && !fullName.isBlank()) ? fullName : "Customer";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from(fromEmail)
                .to(emailAddress)
                .subject("Verify your Lumintra account")
                .html("<p>Hi " + displayName + ",</p><p>Your verification code is: <strong>" + otp + "</strong></p><p>This code expires in 15 minutes.</p>")
                .build();

        try {
            long start = System.currentTimeMillis();
            CreateEmailResponse response = resendClient.emails().send(params);
            long duration = System.currentTimeMillis() - start;

            log.info("OTP Email sent successfully [emailAddress={}] [resendId={}] [duration={}ms]",
                    emailAddress, response.getId(), duration);
        } catch (ResendException exception) {
            log.error("Resend provider failed [emailAddress={}]", emailAddress, exception);
            throw new ExternalProviderUnavailableException("Email provider is temporarily unavailable");
        }
    }
}
