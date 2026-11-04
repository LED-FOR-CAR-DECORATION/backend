package com.lumintra.backend.shared.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAuthConfigTest {

    @Test
    @DisplayName("UTCID01 [N]: googleIdTokenVerifier returns configured GoogleIdTokenVerifier bean")
    void googleIdTokenVerifier_createsConfiguredVerifier() {
        GoogleAuthConfig config = new GoogleAuthConfig();
        ReflectionTestUtils.setField(config, "clientId", "sample-google-client-id.apps.googleusercontent.com");

        GoogleIdTokenVerifier verifier = config.googleIdTokenVerifier();

        assertThat(verifier).isNotNull();
        assertThat(verifier.getAudience()).contains("sample-google-client-id.apps.googleusercontent.com");
    }

    @Test
    @DisplayName("UTCID02 [B]: googleIdTokenVerifier handles empty clientId string")
    void googleIdTokenVerifier_handlesEmptyClientId() {
        GoogleAuthConfig config = new GoogleAuthConfig();
        ReflectionTestUtils.setField(config, "clientId", "");

        GoogleIdTokenVerifier verifier = config.googleIdTokenVerifier();

        assertThat(verifier).isNotNull();
        assertThat(verifier.getAudience()).contains("");
    }
}
