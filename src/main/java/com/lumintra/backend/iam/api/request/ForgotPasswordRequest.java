package com.lumintra.backend.iam.api.request;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.Locale;

@Schema(description = "Request a password reset OTP")
public record ForgotPasswordRequest(
    @Schema(description = "Account email address", example = "customer@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Email is required") @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email must not exceed 254 characters") String email
) {
    public ForgotPasswordRequest { if (email != null) email = email.trim().toLowerCase(Locale.ROOT); }
}
