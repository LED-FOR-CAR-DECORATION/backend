package com.lumintra.backend.iam.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "The registered account email address.", example = "customer@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "The account password.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "The password must not exceed 72 characters.")
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }
    @AssertTrue(message = "The password must not exceed 72 UTF-8 bytes.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
}
