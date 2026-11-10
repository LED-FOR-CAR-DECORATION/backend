package com.lumintra.backend.notification.infrastructure.integration;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.lumintra.backend.shared.exception.notification.InternalUserEmailUnavailableException;

import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

class InternalUserEmailClientTest {
    MockRestServiceServer server;
    InternalUserEmailClient client;

    void setup() {
        var b = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(b).build();
        client = new InternalUserEmailClient(b.build(), "system@example.com");
    }

    void expect(HttpStatus status) {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(header("Idempotency-Key", "delivery-1"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;secret&gt;")))
                .andExpect(
                        content()
                                .string(
                                        org.hamcrest.Matchers.containsString(
                                                "Your Lumintra internal account")))
                .andRespond(withStatus(status));
    }

    @Test
    @DisplayName(
            "A: Send escaped credentials email; retry only transient failures twice with a stable"
                    + " delivery key and typed failures")
    void deliveryAndFailures() {
        setup();
        expect(HttpStatus.OK);
        client.sendCredentials("staff@example.com", "<secret>", "delivery-1");
        server.verify();
        for (var status :
                new HttpStatus[] {HttpStatus.TOO_MANY_REQUESTS, HttpStatus.SERVICE_UNAVAILABLE}) {
            setup();
            expect(status);
            expect(HttpStatus.OK);
            client.sendCredentials("staff@example.com", "<secret>", "delivery-1");
            server.verify();
        }
        setup();
        expect(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThatThrownBy(
                        () -> client.sendCredentials("staff@example.com", "<secret>", "delivery-1"))
                .isInstanceOf(InternalUserEmailUnavailableException.class)
                .hasCauseInstanceOf(
                        org.springframework.web.client.RestClientResponseException.class);
        server.verify();
        setup();
        expect(HttpStatus.SERVICE_UNAVAILABLE);
        expect(HttpStatus.SERVICE_UNAVAILABLE);
        assertThatThrownBy(
                        () -> client.sendCredentials("staff@example.com", "<secret>", "delivery-1"))
                .isInstanceOf(InternalUserEmailUnavailableException.class);
        server.verify();
        setup();
        for (int i = 0; i < 2; i++)
            server.expect(requestTo("https://api.resend.com/emails"))
                    .andExpect(header("Idempotency-Key", "delivery-1"))
                    .andRespond(withException(new SocketTimeoutException("timeout")));
        assertThatThrownBy(
                        () -> client.sendCredentials("staff@example.com", "<secret>", "delivery-1"))
                .isInstanceOf(InternalUserEmailUnavailableException.class);
        server.verify();
    }
}
