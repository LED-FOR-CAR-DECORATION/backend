package com.lumintra.backend.iam.api.request;

import com.lumintra.backend.iam.api.request.validation.ResetPasswordMatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

@Schema(description = "Payload for resetting account password using OTP")
@ResetPasswordMatch
public record ResetPasswordRequest(
        @Schema(
                description = "Account email address",
                example = "customer@example.com",
                maxLength = 254,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 254, message = "Email must not exceed 254 characters")
        String email,

        @Schema(
                description = "6-digit OTP code received via email",
                example = "123456",
                minLength = 6,
                maxLength = 6,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "^\\d{6}$", message = "OTP must be exactly 6 digits")
        String otp,

        @Schema(
                description = "New account password. Must be 8-72 characters long, containing at least one uppercase letter, one lowercase letter, one digit, and one special character (@$!%*?&)",
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
        String newPassword,

        @Schema(
                description = "Optional password confirmation. If supplied, must match newPassword exactly",
                example = "P@ssword123",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        String confirmPassword
) {
    public ResetPasswordRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
        if (otp != null) {
            otp = otp.trim();
        }
    }
}
