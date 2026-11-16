package com.lumintra.backend.store.api.request;


import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

public record UpdateSubscriptionRequest(
        @NotBlank @Size(max = 255) String planName,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        String description,
        @Size(max = 500) @URL @Pattern(regexp = "https?://\\S+") String thumbnailUrl,
        @Positive Integer durationDays,
        @NotNull Boolean allAccessCharacters) {
    public UpdateSubscriptionRequest {
        planName = planName == null ? null : planName.strip();
    }
}
