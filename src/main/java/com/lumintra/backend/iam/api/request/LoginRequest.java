package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Payload for user login authentication")
public record LoginRequest(
        @Schema(
                description = "Registered user email address",
                example = "user@example.com",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(
                description = "Account password",
                example = "P@ssword123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Password is required")
        String password
) {}
