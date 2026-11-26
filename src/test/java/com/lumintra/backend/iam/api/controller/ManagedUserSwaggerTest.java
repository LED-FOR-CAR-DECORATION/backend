package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.api.request.ManagedUserFilterRequest;
import com.lumintra.backend.iam.api.response.ManagedUserListResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ManagedUserSwaggerTest {
    @Test
    @DisplayName("N: Document list response, errors and Bearer authentication")
    void contract() throws Exception {
        var method =
                ManagedUserController.class.getMethod(
                        "list", Authentication.class, ManagedUserFilterRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("200", "400", "401", "403", "500");
    }

    @Test
    @DisplayName("N: Every filter and response field has a documented schema")
    void schemas() {
        for (Class<?> dto :
                new Class<?>[] {
                    ManagedUserFilterRequest.class,
                    ManagedUserListResponse.class,
                    ManagedUserListResponse.UserItem.class
                }) {
            assertThat(dto.getAnnotation(Schema.class)).isNotNull();
            for (var field : dto.getDeclaredFields())
                assertThat(field.getAnnotation(Schema.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("N: Summary field allowlist excludes credentials and security counters")
    void safeFields() {
        assertThat(ManagedUserListResponse.UserItem.class.getRecordComponents())
                .extracting(v -> v.getName())
                .containsExactly(
                        "accountId",
                        "name",
                        "email",
                        "phone",
                        "avatar",
                        "role",
                        "status",
                        "active");
    }
}
