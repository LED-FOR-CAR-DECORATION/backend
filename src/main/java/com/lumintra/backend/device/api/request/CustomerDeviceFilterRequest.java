package com.lumintra.backend.device.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Pagination parameters for listing the authenticated customer's registered LED hardware devices.")
public record CustomerDeviceFilterRequest(
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
        Integer size
) {
    public CustomerDeviceFilterRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }

    public CustomerDeviceFilterRequest() {
        this(0, 20);
    }
}
