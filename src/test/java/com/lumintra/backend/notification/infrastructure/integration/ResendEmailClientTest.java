package com.lumintra.backend.notification.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.lumintra.backend.shared.exception.shared.ExternalProviderUnavailableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ResendEmailClientTest {
    private MockRestServiceServer server;
    private ResendEmailClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ResendEmailClient(builder.build(), "system@example.test");
    }

    @Test
    void sendsOtpWithEscapedDisplayName() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(content().string(containsString("Verify your Lumintra account")))
                .andExpect(content().string(containsString("Hi &lt;Jane&gt;")))
                .andExpect(content().string(containsString("123456")))
                .andExpect(content().string(containsString("15 minutes")))
                .andRespond(withSuccess());
        client.sendOtpEmail("customer@example.test", "<Jane>", "123456");
        server.verify();
    }

    @Test
    void defaultsBlankDisplayName() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(content().string(containsString("Hi Customer")))
                .andRespond(withSuccess());
        client.sendOtpEmail("customer@example.test", " ", "123456");
        server.verify();
    }

    @Test
    void preservesOtpProviderErrorContract() {
        server.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.sendOtpEmail("customer@example.test", "Jane", "123456"))
                .isInstanceOf(ExternalProviderUnavailableException.class)
                .hasMessage("Email provider is temporarily unavailable");
        server.verify();
    }
}
