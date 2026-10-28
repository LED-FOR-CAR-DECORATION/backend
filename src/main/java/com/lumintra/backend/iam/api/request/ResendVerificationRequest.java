package com.lumintra.backend.iam.api.request;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record ResendVerificationRequest(@NotBlank @Email @Size(max = 254) String email) {
    public ResendVerificationRequest { email = email == null ? null : email.strip().toLowerCase(Locale.ROOT); }
}
