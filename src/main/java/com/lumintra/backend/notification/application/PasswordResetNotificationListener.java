package com.lumintra.backend.notification.application;
import com.lumintra.backend.iam.domain.event.PasswordResetRequestedEvent;
import com.lumintra.backend.notification.domain.service.PasswordResetEmailProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PasswordResetNotificationListener {
    private final PasswordResetEmailProvider provider;
    // Synchronous event delivery surfaces provider failures without holding a DB transaction.
    @EventListener public void handle(PasswordResetRequestedEvent event) {
        provider.sendPasswordResetOtp(event.email(), event.otp(), event.deliveryId());
    }
}
