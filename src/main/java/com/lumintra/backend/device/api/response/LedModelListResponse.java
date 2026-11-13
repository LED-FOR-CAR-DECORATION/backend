package com.lumintra.backend.device.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Paginated list of LED hardware models for internal catalog and inventory management")
@Builder
public record LedModelListResponse(
        @Schema(description = "Paginated LED model summaries sorted by modelId descending", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LedModelItem> items,

        @Schema(description = "Standard pagination metadata", requiredMode = Schema.RequiredMode.REQUIRED)
        PaginationMeta meta
) {
    public LedModelListResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    @Schema(description = "Summary representation of an LED hardware model")
    @Builder
    public record LedModelItem(
            @Schema(description = "Unique LED hardware model identifier", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
            Long modelId,

            @Schema(
                    description = "Linked Store product identifier, or null when the model is not yet linked to a Store product",
                    example = "101",
                    nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED
            )
            Long productId,

            @Schema(description = "LED hardware model display name", example = "Lumintra Matrix 64x64 Pro", requiredMode = Schema.RequiredMode.REQUIRED)
            String modelName,

            @Schema(description = "Available sellable warehouse stock quantity", example = "50", requiredMode = Schema.RequiredMode.REQUIRED)
            Long stockQuantity,

            @JsonProperty("isLowStock")
            @Schema(
                    name = "isLowStock",
                    description = "Whether current stockQuantity is below the low-stock alert threshold (< 5)",
                    example = "false",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            boolean isLowStock,

            @Schema(description = "LED model lifecycle status", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
            LedModelStatus status,

            @Schema(description = "Structured technical specifications including canvas dimensions and ordered attributes", requiredMode = Schema.RequiredMode.REQUIRED)
            LedSpec spec
    ) {}

    @Schema(description = "Structured technical specifications for an LED hardware model")
    @Builder
    public record LedSpec(
            @Schema(description = "LED canvas width in pixels", example = "64", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer widthPx,

            @Schema(description = "LED canvas height in pixels", example = "64", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer heightPx,

            @Schema(description = "Ordered list of display specification attributes", requiredMode = Schema.RequiredMode.REQUIRED)
            List<SpecAttribute> attributes
    ) {
        public LedSpec {
            attributes = attributes == null ? List.of() : List.copyOf(attributes);
        }
    }

    @Schema(description = "Single technical specification key-value pair")
    @Builder
    public record SpecAttribute(
            @Schema(description = "Specification attribute label", example = "Voltage", requiredMode = Schema.RequiredMode.REQUIRED)
            String label,

            @Schema(description = "Specification attribute value", example = "5V USB-C", requiredMode = Schema.RequiredMode.REQUIRED)
            String value
    ) {}
}
