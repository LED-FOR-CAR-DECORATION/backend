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
        @NotNull Boolean allAccessCharacters,
        @Min(0) @Max(100) Integer maxCommissionRevisions,
        @Min(1) @Max(100) Integer maxMobileProfiles) {
    public UpdateSubscriptionRequest(
            String planName,
            BigDecimal basePrice,
            String description,
            String thumbnailUrl,
            Integer durationDays,
            Boolean allAccessCharacters) {
        this(planName, basePrice, description, thumbnailUrl, durationDays, allAccessCharacters, null, null);
    }

    public UpdateSubscriptionRequest(
            String planName,
            BigDecimal basePrice,
            String description,
            String thumbnailUrl,
            Integer durationDays,
            Boolean allAccessCharacters,
            Integer maxCommissionRevisions) {
        this(
                planName,
                basePrice,
                description,
                thumbnailUrl,
                durationDays,
                allAccessCharacters,
                maxCommissionRevisions,
                null);
    }

    public UpdateSubscriptionRequest {
        planName = planName == null ? null : planName.strip();
    }
}
