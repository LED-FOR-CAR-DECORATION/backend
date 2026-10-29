package com.lumintra.backend.iam.api.request;

import com.lumintra.backend.iam.api.request.validation.PasswordMatch;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@PasswordMatch
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email cannot exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,72}$",
                message = "Password must be 8-72 characters long, contain at least one uppercase letter, one lowercase letter, one digit, and one special character, with no whitespace"
        )
        String password,

        @NotBlank(message = "Confirm password is required")
        String confirmPassword
) {}