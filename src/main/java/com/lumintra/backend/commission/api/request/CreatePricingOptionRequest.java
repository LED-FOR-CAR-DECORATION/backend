package com.lumintra.backend.commission.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;

@Schema(description = "Request payload for creating an option within a commission pricing rule")
@Builder
public record CreatePricingOptionRequest(
        @Schema(description = "Unique option code within the rule", example = "BG_DETAILED")
        @NotBlank(message = "Option code is required")
        @Size(max = 100, message = "Option code cannot exceed 100 characters")
        String optionCode,

        @Schema(description = "Display label of the option", example = "Phông nền chi tiết / Phong cảnh")
        @NotBlank(message = "Option label is required")
        @Size(max = 255, message = "Option label cannot exceed 255 characters")
        String optionLabel,

        @Schema(description = "Optional custom value string", example = "DETAILED", nullable = true)
        String optionValue,

        @Schema(description = "Price surcharge in VND (must be non-negative)", example = "50000.00")
        @NotNull(message = "Base price is required")
        @DecimalMin(value = "0.00", message = "Base price must be greater than or equal to 0.00")
        BigDecimal basePrice,

        @Schema(description = "Whether this option is selected by default", example = "false")
        Boolean isDefault,

        @Schema(description = "Display order sequence", example = "1")
        Integer displayOrder
) {}
