package com.lumintra.backend.commission.api.request;

import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Partial update of a pricing rule. Every field is optional: {@code null} keeps the current value.
 * {@code configCode} and {@code selectionType} are immutable and only accepted when they match the stored value.
 * System rules additionally lock {@code uiType}, {@code isBasePrice}, quantity bounds, {@code isRequired},
 * the dependency and their own active flag.
 */
@Schema(description = "Partial update of a commission pricing rule; null fields keep their current value")
@Builder
public record UpdatePricingRuleRequest(
        @Schema(description = "Immutable rule code; rejected when it differs from the stored code", nullable = true)
        @Size(max = 100) String configCode,
        @Schema(description = "Rule display name", nullable = true)
        @Size(max = 255) String configName,
        @Schema(description = "Rule helper text", nullable = true)
        String description,
        @Schema(description = "Immutable selection type; rejected when it differs from the stored type", nullable = true)
        SelectionType selectionType,
        @Schema(description = "UI component (custom rules only)", nullable = true)
        UiType uiType,
        @Schema(description = "Contributes to the Base bucket (custom rules only)", nullable = true)
        Boolean isBasePrice,
        @Schema(description = "Minimum quantity (custom QUANTITY rules only)", nullable = true)
        @Min(0) Integer minQuantity,
        @Schema(description = "Maximum quantity (custom QUANTITY rules only)", nullable = true)
        @Min(1) Integer maxQuantity,
        @Schema(description = "Whether a selection is mandatory (custom rules only)", nullable = true)
        Boolean isRequired,
        @Schema(description = "Rule active flag; false deactivates a custom rule, true restores a hidden one", nullable = true)
        Boolean isActive,
        @Schema(description = "Rule display order", nullable = true)
        @PositiveOrZero Integer displayOrder,
        @Schema(description = "New parent option (custom rules only, acyclic, max depth 5)", nullable = true)
        @Positive Long dependsOnOptionId,
        @Schema(description = "true removes the current dependency (custom rules only)", nullable = true)
        Boolean clearDependsOnOption,
        @Schema(description = "Updates to existing options", nullable = true)
        List<@NotNull @Valid OptionUpdate> options,
        @Schema(description = "Options to append (choice rules only)", nullable = true)
        @Size(max = 50) List<@NotNull @Valid NewOption> newOptions,
        @Schema(description = "Options to soft-deactivate", nullable = true)
        List<@NotNull @Positive Long> removedOptionIds
) {
    @Builder
    public record OptionUpdate(
            @NotNull @Positive Long optionId,
            @Size(max = 255) String optionLabel,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            ModifierType modifierType,
            Boolean isActive,
            @PositiveOrZero Integer displayOrder
    ) {
        public OptionUpdate(Long optionId, String optionLabel, BigDecimal basePrice) {
            this(optionId, optionLabel, basePrice, null, null, null);
        }
    }

    @Builder
    public record NewOption(
            @NotBlank @Size(max = 255) String optionLabel,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @Size(max = 100) String matrixDimensions,
            ModifierType modifierType
    ) {
        public NewOption(String optionLabel, BigDecimal basePrice, String matrixDimensions) {
            this(optionLabel, basePrice, matrixDimensions, null);
        }
    }
}
