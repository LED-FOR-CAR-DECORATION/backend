package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Google OAuth2 login request payload containing the Google ID token")
public record GoogleLoginRequest(
        @Schema(
                description = "Raw ID token (JWT) issued by Google OAuth2 Identity Provider",
                example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Google ID token is required")
        String idToken
) {}
