package com.lumintra.backend.notification.application;

import com.lumintra.backend.iam.domain.event.CustomerRegisteredEvent;
import com.lumintra.backend.notification.domain.service.EmailProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private EmailProvider emailProvider;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    @DisplayName("UTCID01 [N]: Handle CustomerRegisteredEvent and dispatch OTP email via EmailProvider")
    void handleCustomerRegistered_dispatchesOtpEmail() {
        CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                101L,
                "customer@example.com",
                "John Doe",
                "123456"
        );

        notificationEventListener.handleCustomerRegistered(event);

        verify(emailProvider).sendOtpEmail("customer@example.com", "John Doe", "123456");
    }

    @Test
    @DisplayName("UTCID02 [B]: Handle CustomerRegisteredEvent when customer fullName is null")
    void handleCustomerRegistered_nullFullNameHandled() {
        CustomerRegisteredEvent event = new CustomerRegisteredEvent(
                102L,
                "customer.null@example.com",
                null,
                "654321"
        );

        notificationEventListener.handleCustomerRegistered(event);

        verify(emailProvider).sendOtpEmail("customer.null@example.com", null, "654321");
    }
}
