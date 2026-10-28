package com.lumintra.backend.iam.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.Locale;

public record EmailVerificationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "[0-9]{6}") @Schema(example = "123456", description = "Six-digit email verification code. Expires after five minutes.") String otp
) {
    public EmailVerificationRequest { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
}
