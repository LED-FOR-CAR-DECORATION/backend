package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.shared.exception.shared.ExternalProviderUnavailableException;
import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.Emails;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResendEmailClientTest {

    @Mock
    private Resend resendClient;

    @Mock
    private Emails emailsService;

    private ResendEmailClient resendEmailClient;

    @BeforeEach
    void setUp() {
        resendEmailClient = new ResendEmailClient("test-api-key", "noreply@lumintra.com");
        ReflectionTestUtils.setField(resendEmailClient, "resendClient", resendClient);
    }

    @Test
    @DisplayName("UTCID01 [N]: Send OTP email successfully via Resend SDK")
    void sendOtpEmail_success() throws ResendException {
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("resend-msg-12345");
        when(resendClient.emails()).thenReturn(emailsService);
        when(emailsService.send(any(CreateEmailOptions.class))).thenReturn(response);

        resendEmailClient.sendOtpEmail("customer@example.com", "John Doe", "123456");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emailsService).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getFrom()).isEqualTo("noreply@lumintra.com");
        assertThat(options.getTo()).contains("customer@example.com");
        assertThat(options.getSubject()).isEqualTo("Verify your Lumintra account");
        assertThat(options.getHtml()).contains("Hi John Doe").contains("123456");
    }

    @Test
    @DisplayName("UTCID02 [B]: Send OTP email defaults display name to 'Customer' when fullName is null or blank")
    void sendOtpEmail_defaultsDisplayNameWhenNullOrBlank() throws ResendException {
        CreateEmailResponse response = mock(CreateEmailResponse.class);
        when(response.getId()).thenReturn("resend-msg-67890");
        when(resendClient.emails()).thenReturn(emailsService);
        when(emailsService.send(any(CreateEmailOptions.class))).thenReturn(response);

        resendEmailClient.sendOtpEmail("anonymous@example.com", "   ", "999888");

        ArgumentCaptor<CreateEmailOptions> optionsCaptor = ArgumentCaptor.forClass(CreateEmailOptions.class);
        verify(emailsService).send(optionsCaptor.capture());

        CreateEmailOptions options = optionsCaptor.getValue();
        assertThat(options.getHtml()).contains("Hi Customer").contains("999888");
    }

    @Test
    @DisplayName("UTCID03 [A]: Catch ResendException and translate to ExternalProviderUnavailableException")
    void sendOtpEmail_resendFailureThrowsExternalProviderUnavailableException() throws ResendException {
        when(resendClient.emails()).thenReturn(emailsService);
        when(emailsService.send(any(CreateEmailOptions.class)))
                .thenThrow(new ResendException("Resend API rate limit or outage"));

        assertThatThrownBy(() -> resendEmailClient.sendOtpEmail("customer@example.com", "Jane", "123456"))
                .isInstanceOf(ExternalProviderUnavailableException.class)
                .hasMessage("Email provider is temporarily unavailable");
    }
}
