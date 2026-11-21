package com.lumintra.backend.device.api.request;

import com.lumintra.backend.device.domain.model.FirmwareStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

@Schema(description = "Pagination and filters for the firmware release catalog (DEV-11), combined with AND.")
public record FirmwareFilterRequest(
        @Schema(description = "Zero-based page index", example = "0", defaultValue = "0")
        @Min(value = 0, message = "page must be greater than or equal to 0")
        @Max(value = 1_000_000, message = "page must not exceed 1000000")
        Integer page,

        @Schema(description = "Items per page (1 to 100)", example = "20", defaultValue = "20")
        @Min(value = 1, message = "size must be at least 1")
        @Max(value = 100, message = "size must not exceed 100")
        Integer size,

        @Schema(description = "Only releases of this LED model", example = "3")
        @Positive(message = "modelId must be a positive integer")
        Long modelId,

        @Schema(description = "Only releases in this status (ACTIVE, SUPERSEDED, DEPRECATED)", example = "ACTIVE")
        FirmwareStatus status
) {
    public FirmwareFilterRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
