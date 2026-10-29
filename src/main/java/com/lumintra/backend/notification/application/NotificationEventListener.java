package com.lumintra.backend.notification.application;

import com.lumintra.backend.iam.domain.event.CustomerRegisteredEvent;
import com.lumintra.backend.notification.domain.service.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final EmailProvider emailProvider;

    @TransactionalEventListener
    public void handleCustomerRegistered(CustomerRegisteredEvent event) {
        emailProvider.sendOtpEmail(event.email(), event.fullName(), event.otp());
    }
}
