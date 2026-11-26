package com.lumintra.backend.iam.api.controller;

import static org.assertj.core.api.Assertions.*;

import com.lumintra.backend.iam.api.response.ManagedUserDetailResponse;
import com.lumintra.backend.iam.domain.event.UserDetailViewedEvent;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ManagedUserDetailSwaggerTest {
    @Test
    @DisplayName(
            "N: Swagger covers real responses and DTO/event allowlists exclude credentials and"
                    + " unnecessary audit PII")
    void schemasAndAllowlist() throws Exception {
        var method =
                ManagedUserDetailController.class.getMethod(
                        "detail", Authentication.class, long.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(SecurityRequirement.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value())
                .extracting(v -> v.responseCode())
                .containsExactlyInAnyOrder("200", "400", "401", "403", "404", "500");
        for (Class<?> dto :
                new Class<?>[] {
                    ManagedUserDetailResponse.class, ManagedUserDetailResponse.LockoutState.class
                }) {
            assertThat(dto.getAnnotation(Schema.class)).isNotNull();
            for (var component : dto.getRecordComponents()) {
                assertThat(dto.getDeclaredField(component.getName()).getAnnotation(Schema.class))
                        .isNotNull();
            }
        }
        assertThat(ManagedUserDetailResponse.class.getRecordComponents())
                .extracting(v -> v.getName())
                .containsExactly(
                        "accountId",
                        "name",
                        "email",
                        "phone",
                        "avatar",
                        "role",
                        "status",
                        "createdAt",
                        "updatedAt",
                        "lockout");
        assertThat(UserDetailViewedEvent.class.getRecordComponents())
                .extracting(v -> v.getName())
                .containsExactly("viewerAccountId", "targetAccountId", "viewerRole", "viewedAt");
    }
}
