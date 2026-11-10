package com.lumintra.backend.notification.application;
import com.lumintra.backend.iam.domain.event.PasswordResetRequestedEvent;
import com.lumintra.backend.notification.domain.service.PasswordResetEmailProvider;
import com.lumintra.backend.shared.exception.iam.PasswordResetUnavailableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class PasswordResetNotificationListenerTest {
    @Mock PasswordResetEmailProvider provider; @InjectMocks PasswordResetNotificationListener listener;
    @Test @DisplayName("N: Event delivers reset email with its idempotency key") void forwards() {
        listener.handle(new PasswordResetRequestedEvent("a@b.co", "012345", "delivery-1"));
        verify(provider).sendPasswordResetOtp("a@b.co", "012345", "delivery-1");
    }
    @Test @DisplayName("A: Delivery failure is not silently swallowed") void failure() {
        doThrow(new PasswordResetUnavailableException()).when(provider).sendPasswordResetOtp(anyString(), anyString(), anyString());
        assertThatThrownBy(() -> listener.handle(new PasswordResetRequestedEvent("a@b.co", "012345", "delivery-1"))).isInstanceOf(PasswordResetUnavailableException.class);
    }
}
