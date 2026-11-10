package com.lumintra.backend.iam.application;
import com.lumintra.backend.iam.api.request.ForgotPasswordRequest;
import com.lumintra.backend.iam.domain.event.PasswordResetRequestedEvent;
import com.lumintra.backend.iam.domain.service.PasswordResetOtpStore;
import com.lumintra.backend.iam.infrastructure.persistence.repository.UserAccountRepository;
import com.lumintra.backend.shared.exception.iam.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordUseCaseTest {
    @Mock UserAccountRepository accounts; @Mock PasswordResetOtpStore otpStore;
    @Mock ApplicationEventPublisher events; @InjectMocks ForgotPasswordUseCase useCase;
    @Test @DisplayName("N: Existing normalized email stores a six-digit OTP before publishing")
    void existingEmail() {
        when(accounts.existsByEmailIgnoreCase("customer@example.com")).thenReturn(true);
        var result = useCase.execute(new ForgotPasswordRequest("  Customer@Example.com  "));
        var event = ArgumentCaptor.forClass(PasswordResetRequestedEvent.class);
        var order = inOrder(otpStore, events);
        order.verify(otpStore).issue(eq("customer@example.com"), matches("[0-9]{6}"));
        order.verify(events).publishEvent(event.capture());
        assertThat(event.getValue().otp()).matches("[0-9]{6}");
        assertThat(event.getValue().deliveryId()).isNotBlank();
        assertThat(result.message()).contains("If the email is registered");
        assertThat(event.getValue().toString()).doesNotContain(event.getValue().otp(), "customer@example.com");
    }
    @Test @DisplayName("N: Unknown email returns the same acknowledgement without sending email")
    void unknownEmail() {
        var result = useCase.execute(new ForgotPasswordRequest("unknown@example.com"));
        verify(otpStore).issue("unknown@example.com", null); verifyNoInteractions(events);
        assertThat(result.message()).isEqualTo("If the email is registered, a password reset code has been sent.");
    }
    @Test @DisplayName("B: Fourth request is rejected before email delivery")
    void lockedEmail() {
        when(accounts.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        doThrow(new PasswordResetLockedException()).when(otpStore).issue(anyString(), anyString());
        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordRequest("a@b.co"))).isInstanceOf(PasswordResetLockedException.class);
        verifyNoInteractions(events);
    }
    @Test @DisplayName("A: Redis outage prevents email delivery")
    void storeOutage() {
        doThrow(new PasswordResetUnavailableException()).when(otpStore).issue(anyString(), isNull());
        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordRequest("a@b.co"))).isInstanceOf(PasswordResetUnavailableException.class);
        verifyNoInteractions(events);
    }
    @Test @DisplayName("A: Synchronous provider failure is propagated")
    void providerOutage() {
        when(accounts.existsByEmailIgnoreCase(anyString())).thenReturn(true);
        doThrow(new PasswordResetUnavailableException()).when(events).publishEvent(any(Object.class));
        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordRequest("a@b.co"))).isInstanceOf(PasswordResetUnavailableException.class);
    }
    @Test @DisplayName("A: Database outage does not become unknown-email success")
    void databaseOutage() {
        when(accounts.existsByEmailIgnoreCase(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> useCase.execute(new ForgotPasswordRequest("a@b.co"))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        verifyNoInteractions(otpStore, events);
    }
}
