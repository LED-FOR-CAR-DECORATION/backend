package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Management pricing configuration rule detail including options, primitive bounds, active status, and system lock flags")
@Builder
public record ManagementPricingRuleResponse(
        @Schema(description = "Unique configuration rule ID", example = "1")
        Long configId,

        @Schema(description = "Unique rule code identifier", example = "FORMAT_TYPE")
        String configCode,

        @Schema(description = "Display name of the rule", example = "Định dạng hiển thị (Format Type)")
        String configName,

        @Schema(description = "Detailed prompt or helper description", example = "Chọn định dạng ảnh tĩnh hoặc ảnh động GIF")
        String description,

        @Schema(description = "Selection cardinality or input primitive type", example = "SINGLE_SELECT")
        SelectionType selectionType,

        @Schema(description = "UI component presentation type", example = "RADIO")
        UiType uiType,

        @Schema(description = "Whether this rule contributes to the Base price bucket", example = "false")
        boolean isBasePrice,

        @Schema(description = "Minimum allowed quantity when selectionType is QUANTITY", example = "0", nullable = true)
        Integer minQuantity,

        @Schema(description = "Maximum allowed quantity when selectionType is QUANTITY", example = "5", nullable = true)
        Integer maxQuantity,

        @Schema(description = "Whether selection is mandatory", example = "true")
        boolean isRequired,

        @Schema(description = "Whether this is a locked system rule", example = "true")
        boolean isSystem,

        @Schema(description = "Whether the rule is active and visible to customers", example = "true")
        boolean isActive,

        @Schema(description = "Display order sequence", example = "1")
        int displayOrder,

        @Schema(description = "Parent option ID this rule depends on (null if root rule)", example = "2", nullable = true)
        Long dependsOnOptionId,

        @Schema(description = "Parent rule code this rule depends on", example = "FORMAT_TYPE", nullable = true)
        String dependsOnRuleKey,

        @Schema(description = "Parent option code this rule depends on", example = "ANIMATED_GIF", nullable = true)
        String dependsOnOptionCode,

        @Schema(description = "All options configured for this rule (both active and inactive)")
        List<ManagementPricingOptionResponse> options
) {}
