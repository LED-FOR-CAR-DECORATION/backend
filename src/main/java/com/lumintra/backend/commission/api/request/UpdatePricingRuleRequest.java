package com.lumintra.backend.commission.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record UpdatePricingRuleRequest(
        @NotBlank @Size(max = 255) String configName,
        String description,
        @NotNull Boolean isRequired,
        @NotNull List<@NotNull @Valid OptionUpdate> options,
        @Size(max = 50) List<@NotNull @Valid NewOption> newOptions
) {
    public record OptionUpdate(
            @NotNull @Positive Long optionId,
            @NotBlank @Size(max = 255) String optionLabel,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice
    ) {}

    public record NewOption(
            @NotBlank @Size(max = 255) String optionLabel,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @Size(max = 100) String matrixDimensions
    ) {}
}
