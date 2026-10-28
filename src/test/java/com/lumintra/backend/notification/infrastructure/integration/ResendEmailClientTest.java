package com.lumintra.backend.notification.infrastructure.integration;

import com.lumintra.backend.shared.exception.iam.EmailVerificationException;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;

class ResendEmailClientTest {
    MockRestServiceServer server; ResendEmailClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ResendEmailClient(builder.build(), "noreply@example.com");
    }
    @Test void retriesTransientFailureWithTheSameIdempotencyKey() {
        server.expect(requestTo("https://api.resend.com/emails")).andExpect(header("Idempotency-Key", "test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo("https://api.resend.com/emails")).andExpect(header("Idempotency-Key", "test-key"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("123456"))).andRespond(withSuccess());
        client.sendRegistrationOtp("customer@example.com", "123456", "test-key"); server.verify();
    }
    @Test void rejectsProviderErrorsWithoutLeakingProviderResponse() {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.FORBIDDEN).body("private provider details"));
        assertThatThrownBy(()->client.sendRegistrationOtp("customer@example.com", "123456", "test-key"))
                .isInstanceOf(EmailVerificationException.class).hasMessageNotContaining("private provider details");
        server.verify();
    }
    @Test void stopsAfterOneRetryWhenProviderRemainsUnavailable() {
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(()->client.sendRegistrationOtp("customer@example.com", "123456", "test-key"))
                .isInstanceOf(EmailVerificationException.class); server.verify();
    }
}
