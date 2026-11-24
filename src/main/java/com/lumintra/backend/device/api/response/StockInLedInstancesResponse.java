package com.lumintra.backend.device.api.response;

import com.lumintra.backend.device.domain.model.LedModelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Builder
@Schema(description = "Result of stocking in physical LED instances for an LED hardware model")
public record StockInLedInstancesResponse(
        @Schema(description = "Target LED hardware model identifier", example = "1")
        Long modelId,

        @Schema(description = "LED hardware model display name", example = "Lumintra Matrix 64×64 Pro")
        String modelName,

        @Schema(description = "Number of physical instances added in this batch", example = "5")
        int importedCount,

        @Schema(description = "Updated available stock quantity on the LED model", example = "15")
        Long stockQuantity,

        @Schema(description = "Updated LED model availability status", example = "ACTIVE")
        LedModelStatus modelStatus,

        @Schema(description = "Created IN_STOCK LED instances with signed factory QR payloads")
        List<LedInstanceListResponse.LedInstanceItem> items
) {}
