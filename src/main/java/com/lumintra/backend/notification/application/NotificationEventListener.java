package com.lumintra.backend.notification.application;

import com.lumintra.backend.iam.domain.event.CustomerRegisteredEvent;
import com.lumintra.backend.notification.domain.service.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final EmailProvider emailProvider;

    @Async
    @TransactionalEventListener
    public void handleCustomerRegistered(CustomerRegisteredEvent event) {
        emailProvider.sendOtpEmail(event.email(), event.fullName(), event.otp());
    }

    @Async
    @TransactionalEventListener
    public void handleVerificationEmailResent(com.lumintra.backend.iam.domain.event.VerificationEmailResentEvent event) {
        // Passing null for fullName allows ResendEmailClient to gracefully default to "Customer"
        emailProvider.sendOtpEmail(event.email(), null, event.otp());
    }
}
