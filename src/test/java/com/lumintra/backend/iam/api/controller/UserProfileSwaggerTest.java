package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.api.response.CurrentProfileResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import org.junit.jupiter.api.*;

class UserProfileSwaggerTest {
    @Test
    @DisplayName("N: Document success and applicable errors")
    void contract() throws Exception {
        var method =
                UserProfileController.class.getMethod(
                        "me", org.springframework.security.core.Authentication.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("200", "401", "403", "404", "500");
    }

    @Test
    @DisplayName("N: DTO uses an explicit safe field allowlist with schemas")
    void safeFields() {
        assertThat(CurrentProfileResponse.class.getRecordComponents())
                .extracting(v -> v.getName())
                .containsExactly("accountId", "name", "email", "phone", "avatar", "roles");
        for (var field : CurrentProfileResponse.class.getDeclaredFields())
            assertThat(field.getAnnotation(Schema.class)).isNotNull();
    }
}
