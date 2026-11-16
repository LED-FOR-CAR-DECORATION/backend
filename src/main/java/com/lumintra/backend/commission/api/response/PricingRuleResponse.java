package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.List;

@Schema(description = "Commission pricing configuration rule")
@Builder
public record PricingRuleResponse(
        @Schema(description = "Unique configuration rule ID", example = "1")
        Long configId,

        @Schema(description = "Unique rule key code", example = "FORMAT_TYPE")
        String ruleKey,

        @Schema(description = "Display name of the rule", example = "Định dạng hiển thị (Format Type)")
        String configName,

        @Schema(description = "Detailed prompt or helper description", example = "Chọn định dạng ảnh tĩnh hoặc ảnh động GIF")
        String description,

        @Schema(description = "Selection cardinality type", example = "SINGLE_SELECT")
        SelectionType selectionType,

        @Schema(description = "UI component presentation type", example = "RADIO")
        UiType uiType,

        @Schema(description = "Whether selection is mandatory", example = "true")
        boolean isRequired,

        @Schema(description = "Whether this is a locked system rule", example = "true")
        boolean isSystem,

        @Schema(description = "Display order sequence", example = "1")
        int displayOrder,

        @Schema(description = "Parent option ID this rule depends on (null if root rule)", example = "2", nullable = true)
        Long dependsOnOptionId,

        @Schema(description = "Parent rule key this rule depends on", example = "FORMAT_TYPE", nullable = true)
        String dependsOnRuleKey,

        @Schema(description = "Parent option code this rule depends on", example = "ANIMATED_GIF", nullable = true)
        String dependsOnOptionCode,

        @Schema(description = "Available selectable options for this rule")
        List<PricingOptionResponse> options
) {}
