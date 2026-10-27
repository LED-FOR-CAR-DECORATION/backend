package com.lumintra.backend.iam.api.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

@Schema(description = "Customer registration. Email is trimmed and lowercased; the password is preserved exactly.")
public record RegisterRequest(
        @Schema(description = "Full name, 2-100 characters, without control or format characters.", example = "John Doe", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 100, message = "Full name must contain 2 to 100 characters.")
        @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Full name contains invalid characters.")
        String fullName,
        @Schema(description = "A valid, unregistered email address with at most 254 characters.", example = "customer@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Email is required.")
        @Email(message = "The email address is invalid.")
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "The email address is invalid.")
        @Size(max = 254, message = "Email must not exceed 254 characters.")
        String email,
        @Schema(description = "8-72 characters and at most 72 UTF-8 bytes; requires uppercase and lowercase letters, a digit and a special character; no whitespace.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "The password must contain 8 to 72 characters.")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9\\s])[^\\s]+$",
                message = "The password must include uppercase and lowercase letters, a digit and a special character, with no whitespace.")
        String password,
        @Schema(description = "Must match password.", example = "Secure123!", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Password confirmation is required.")
        String confirmPassword
) {
    public RegisterRequest {
        fullName = fullName == null ? null : fullName.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @AssertTrue(message = "The password confirmation does not match.")
    @Schema(hidden = true)
    public boolean isPasswordsMatch() {
        return Objects.equals(password, confirmPassword);
    }

    @AssertTrue(message = "The password must not exceed 72 UTF-8 bytes.")
    @Schema(hidden = true)
    public boolean isPasswordWithinLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
