package com.lumintra.backend.commission.api.request;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;

@Schema(description = "Request payload for creating a custom commission pricing rule")
@Builder
public record CreatePricingRuleRequest(
        @Schema(description = "Unique rule configuration code/key", example = "BACKGROUND_COMPLEXITY")
        @NotBlank(message = "Rule code is required")
        @Size(max = 100, message = "Rule code cannot exceed 100 characters")
        String configCode,

        @Schema(description = "Display name of the rule", example = "Độ phức tạp hình nền")
        @NotBlank(message = "Rule name is required")
        @Size(max = 255, message = "Rule name cannot exceed 255 characters")
        String configName,

        @Schema(description = "Detailed prompt or helper description", example = "Chọn mức độ chi tiết của phông nền")
        String description,

        @Schema(description = "Selection cardinality type", example = "SINGLE_SELECT")
        @NotNull(message = "Selection type is required")
        SelectionType selectionType,

        @Schema(description = "UI component presentation type", example = "RADIO")
        @NotNull(message = "UI type is required")
        UiType uiType,

        @Schema(description = "Whether selection is mandatory", example = "true")
        Boolean isRequired,

        @Schema(description = "Display order sequence", example = "4")
        Integer displayOrder,

        @Schema(description = "Parent option ID this rule conditionally depends on", example = "2", nullable = true)
        Long dependsOnOptionId,

        @Schema(description = "List of selectable options configured for this rule")
        @Valid
        List<CreatePricingOptionRequest> options
) {}
