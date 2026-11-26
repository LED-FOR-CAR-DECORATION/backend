package com.lumintra.backend.notification.application;

import com.lumintra.backend.iam.domain.event.VerificationEmailResentEvent;
import com.lumintra.backend.notification.domain.service.EmailProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerResendTest {

    @Mock
    private EmailProvider emailProvider;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    @DisplayName("UTCID01 [N]: Handle VerificationEmailResentEvent and dispatch OTP email with null fullName")
    void handleVerificationEmailResent_dispatchesOtpEmailWithNullFullName() {
        VerificationEmailResentEvent event = new VerificationEmailResentEvent(
                "customer@example.com",
                "123456"
        );

        notificationEventListener.handleVerificationEmailResent(event);

        verify(emailProvider).sendOtpEmail("customer@example.com", null, "123456");
    }

    @Test
    @DisplayName("UTCID02 [B]: Handle VerificationEmailResentEvent with zero-padded boundary OTP ('000123')")
    void handleVerificationEmailResent_boundaryZeroPaddedOtp() {
        VerificationEmailResentEvent event = new VerificationEmailResentEvent(
                "boundary@example.com",
                "000123"
        );

        notificationEventListener.handleVerificationEmailResent(event);

        verify(emailProvider).sendOtpEmail("boundary@example.com", null, "000123");
    }
}
