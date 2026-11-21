package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Request body for updating an LED hardware model")
public record UpdateLedModelRequest(
        @Schema(description = "Unique LED hardware model display name", example = "Lumintra Matrix 64x64 Pro V2 (Nâng cấp)", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "modelName is required")
        @Size(max = 255, message = "modelName must not exceed 255 characters")
        String modelName,

        @Schema(description = "Warehouse stock quantity (>= 0)", example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "stockQuantity is required")
        @Min(value = 0, message = "stockQuantity must be greater than or equal to 0")
        Long stockQuantity,

        @Schema(description = "Structured technical specifications", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "spec is required")
        @Valid
        LedSpecRequest spec
) {
    public UpdateLedModelRequest {
        if (modelName != null) {
            modelName = modelName.strip();
        }
    }

    @Schema(description = "Hardware canvas dimensions and ordered specification attributes")
    public record LedSpecRequest(
            @Schema(description = "LED matrix canvas width in pixels", example = "64", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "widthPx is required")
            @Positive(message = "widthPx must be greater than 0")
            Integer widthPx,

            @Schema(description = "LED matrix canvas height in pixels", example = "64", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "heightPx is required")
            @Positive(message = "heightPx must be greater than 0")
            Integer heightPx,

            @Schema(description = "Ordered list of key-value technical specifications", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            List<@Valid SpecAttributeRequest> attributes
    ) {
        public LedSpecRequest {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }
    }

    @Schema(description = "Specification key-value entry")
    public record SpecAttributeRequest(
            @Schema(description = "Specification attribute label", example = "Điện áp", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "attribute label is required")
            @Size(max = 100, message = "attribute label must not exceed 100 characters")
            String label,

            @Schema(description = "Specification attribute value", example = "5V USB-C PD", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "attribute value is required")
            @Size(max = 500, message = "attribute value must not exceed 500 characters")
            String value
    ) {
        public SpecAttributeRequest {
            if (label != null) {
                label = label.strip();
            }
            if (value != null) {
                value = value.strip();
            }
        }
    }
}
