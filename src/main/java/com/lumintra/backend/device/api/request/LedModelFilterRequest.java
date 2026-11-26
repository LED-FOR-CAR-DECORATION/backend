package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.domain.model.LedModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Pagination and filter parameters for listing LED hardware models, combined with AND.")
public record LedModelFilterRequest(
        @Schema(
                description = "Zero-based page index (0 to 1,000,000)",
                example = "0",
                defaultValue = "0",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Min(value = 0, message = "page must be greater than or equal to 0")
        @Max(value = 1_000_000, message = "page must not exceed 1000000")
        Integer page,

        @Schema(
                description = "Number of items per page (1 to 100)",
                example = "20",
                defaultValue = "20",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Integer size,

        @Schema(
                description = "Filter by LED model lifecycle status (ACTIVE, OUT_OF_STOCK, DEPRECATED)",
                example = "ACTIVE",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LedModelStatus status,

        @Schema(
                description = "Filter by LED canvas width in pixels",
                example = "64",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Positive(message = "widthPx must be greater than 0")
        @Max(value = 4096, message = "widthPx must not exceed 4096")
        Integer widthPx,

        @Schema(
                description = "Filter by LED canvas height in pixels",
                example = "64",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Positive(message = "heightPx must be greater than 0")
        @Max(value = 4096, message = "heightPx must not exceed 4096")
        Integer heightPx,

        @Schema(
                description = "When true, returns only LED models not yet linked to any Store product (productId is null)",
                example = "true",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Boolean unlinkedToProductOnly,

        @Schema(
                description = "When true, returns only low-stock LED models (stockQuantity < 5)",
                example = "false",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        Boolean lowStockOnly,

        @Schema(
                description = "Case-insensitive literal substring search on modelName; blank is ignored",
                example = "Matrix 64x64",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 255, message = "keyword must not exceed 255 characters")
        String keyword
) {
    public LedModelFilterRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        keyword = (keyword == null || keyword.isBlank()) ? null : keyword.strip();
    }
}
