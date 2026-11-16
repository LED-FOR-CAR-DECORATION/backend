package com.lumintra.backend.store.api.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.*;

import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;

public record CreateSubscriptionRequest(
        @NotBlank @Size(max = 255) String planName,
        @NotBlank @Size(max = 100) String planCode,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        String description,
        @Size(max = 500) @URL @Pattern(regexp = "https?://\\S+") String thumbnailUrl,
        @JsonProperty("isLifetime") @NotNull Boolean lifetime,
        @Positive Integer durationDays,
        @NotNull Boolean allAccessCharacters,
        List<@NotNull @Positive Long> tagIds) {
    public CreateSubscriptionRequest {
        planName = planName == null ? null : planName.strip();
        planCode = planCode == null ? null : planCode.strip();
    }

    @JsonIgnore
    @AssertTrue(
            message =
                    "durationDays is required for a fixed-term plan and must be omitted for a"
                        + " lifetime plan")
    public boolean isDurationValid() {
        return lifetime == null || lifetime == (durationDays == null);
    }
}
