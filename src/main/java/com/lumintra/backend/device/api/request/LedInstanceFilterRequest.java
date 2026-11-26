package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Pagination and filter parameters for listing and tracking physical LED instances, combined with AND.")
public record LedInstanceFilterRequest(
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
                description = "Filter by parent LED hardware model identifier",
                example = "101",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Positive(message = "modelId must be greater than 0")
        Long modelId,

        @Schema(
                description = "Filter by physical instance lifecycle status (IN_STOCK, IN_TRANSIT, BOUND, RETURNED, DECOMMISSIONED)",
                example = "IN_STOCK",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        LedInstanceStatus status,

        @Schema(
                description = "Case-insensitive literal substring search on serialNumber or macAddress; blank is ignored",
                example = "AA:BB:CC",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        @Size(max = 255, message = "search must not exceed 255 characters")
        String search
) {
    public LedInstanceFilterRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        search = (search == null || search.isBlank()) ? null : search.strip();
    }
}
