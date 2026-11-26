package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "User logout request payload containing the refresh token to be revoked")
public record LogoutRequest(
        @Schema(
                description = "Refresh token to be invalidated and revoked upon logout",
                example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {}
