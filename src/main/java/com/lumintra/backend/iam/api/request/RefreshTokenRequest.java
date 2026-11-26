package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token request payload containing the single-use refresh token")
public record RefreshTokenRequest(
        @Schema(
                description = "Signed JWT refresh token previously issued upon login or token renewal",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
