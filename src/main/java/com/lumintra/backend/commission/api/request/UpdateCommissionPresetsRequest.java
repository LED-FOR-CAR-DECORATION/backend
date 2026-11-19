package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Upserts commission presets and the CUSTOM base mode, keyed by {@code tierCode}. Each listed tier is fully
 * replaced; an unknown code creates a new preset. Tiers not listed are left unchanged.
 */
@Schema(description = "Commission presets and the CUSTOM base mode to create or replace, keyed by tierCode")
@Builder
public record UpdateCommissionPresetsRequest(
        @Schema(description = "Tiers to create or replace; tiers not listed are unchanged")
        @NotEmpty @Size(max = 20) List<@NotNull @Valid Preset> presets
) {

    @Schema(description = "Full configuration of one preset or of the CUSTOM base mode")
    @Builder
    public record Preset(
            @Schema(description = "Tier code (case-insensitive, stored upper-case). CUSTOM is the base mode", example = "BASIC")
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{0,49}$",
                    message = "must start with a letter and contain only letters, digits or underscores (max 50)")
            String tierCode,
            @Schema(description = "Display name", example = "Gói Cơ Bản")
            @NotBlank @Size(max = 255) String tierName,
            @Schema(description = "Description", nullable = true)
            @Size(max = 2000) String description,
            @Schema(description = "Fixed package price for presets, base fee for CUSTOM", example = "150000")
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
            @Schema(description = "Bundle discount percent (presets only; CUSTOM must be 0). Defaults to 0", nullable = true)
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountPercent,
            @Schema(description = "Free revisions: BASIC = 1, ADVANCED = 3, others 1..5", example = "1")
            @NotNull @Min(1) @Max(5) Integer maxRevisions,
            @Schema(description = "Paid revision fee as a percent of the initial price. Defaults to 10", nullable = true)
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal extraRevisionFeePercent,
            @Schema(description = "Active flag; CUSTOM cannot be deactivated. Defaults to true", nullable = true)
            Boolean isActive,
            @Schema(description = "Display order. Defaults to 0", nullable = true)
            @PositiveOrZero Integer displayOrder,
            @Schema(description = "Options the customer must select for this preset to apply; empty = all requests (presets only)",
                    nullable = true)
            @Size(max = 50) List<@NotNull @Positive Long> triggerOptionIds,
            @Schema(description = "Bundled options from active choice rules (presets only)", nullable = true)
            @Size(max = 50) List<@NotNull @Positive Long> includedOptionIds
    ) {
    }
}
