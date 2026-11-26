package com.lumintra.backend.notification.infrastructure.integration;
import com.lumintra.backend.shared.exception.iam.PasswordResetUnavailableException;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.HttpStatus;
import java.net.SocketTimeoutException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
class PasswordResetEmailClientTest {
    MockRestServiceServer server; PasswordResetEmailClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PasswordResetEmailClient(builder.build(), "reset@example.com");
    }
    void expect(HttpStatus status) {
        server.expect(requestTo("https://api.resend.com/emails")).andExpect(header("Idempotency-Key", "delivery-1"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("5 minutes")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Reset your Lumintra password")))
            .andRespond(withStatus(status));
    }
    @Test @DisplayName("N: Send reset-specific email with five-minute lifetime") void success() {
        expect(HttpStatus.OK); client.sendPasswordResetOtp("a@b.co", "012345", "delivery-1"); server.verify();
    }
    @Test @DisplayName("A: Rate-limit response retries once using the same idempotency key") void rateLimit() {
        expect(HttpStatus.TOO_MANY_REQUESTS); expect(HttpStatus.OK);
        client.sendPasswordResetOtp("a@b.co", "012345", "delivery-1"); server.verify();
    }
    @Test @DisplayName("A: Permanent rejection is not retried and preserves cause") void rejection() {
        expect(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(() -> client.sendPasswordResetOtp("a@b.co", "012345", "delivery-1"))
            .isInstanceOf(PasswordResetUnavailableException.class).hasCauseInstanceOf(org.springframework.web.client.RestClientResponseException.class);
        server.verify();
    }
    @Test @DisplayName("A: Server errors stop after two attempts") void serverFailure() {
        expect(HttpStatus.SERVICE_UNAVAILABLE); expect(HttpStatus.SERVICE_UNAVAILABLE);
        assertThatThrownBy(() -> client.sendPasswordResetOtp("a@b.co", "012345", "delivery-1")).isInstanceOf(PasswordResetUnavailableException.class);
        server.verify();
    }
    @Test @DisplayName("A: Timeout retries once and returns a typed service failure") void timeout() {
        for (int i=0;i<2;i++) server.expect(requestTo("https://api.resend.com/emails")).andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(() -> client.sendPasswordResetOtp("a@b.co", "012345", "delivery-1")).isInstanceOf(PasswordResetUnavailableException.class);
        server.verify();
    }
}
