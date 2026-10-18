package com.lumintra.backend.notification.application;

import com.lumintra.backend.notification.domain.service.EmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final EmailProvider emailProvider;

// EXAMPLE IMPLEMENTATION
//    @TransactionalEventListener
//    public void handleOrderCreated(OrderCreatedEvent event) {
//        emailProvider.sendWelcomeEmail(event.customerEmail(), event.customerName());
//    }
}
