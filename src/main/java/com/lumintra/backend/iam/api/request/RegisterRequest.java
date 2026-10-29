package com.lumintra.backend.iam.api.request;

import com.lumintra.backend.iam.api.request.validation.PasswordMatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for registering a new customer account")
@PasswordMatch
public record RegisterRequest(
        @Schema(
                description = "Customer email address. Must be unique and valid format",
                example = "customer@example.com",
                maxLength = 255,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @Schema(
                description = "Account password. Must be 8-72 characters long, containing at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)",
                example = "P@ssword123",
                minLength = 8,
                maxLength = 72,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$",
                message = "Password must be 8-72 characters long, contain at least one uppercase letter, one lowercase letter, one digit, and one special character, with no whitespace"
        )
        String password,

        @Schema(
                description = "Password confirmation. Must match password field exactly",
                example = "P@ssword123",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}