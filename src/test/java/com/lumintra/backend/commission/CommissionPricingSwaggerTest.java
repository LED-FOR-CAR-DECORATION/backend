package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.controller.CommissionPricingController;
import com.lumintra.backend.commission.api.response.PricingOptionResponse;
import com.lumintra.backend.commission.api.response.PricingRuleResponse;
import com.lumintra.backend.commission.api.response.PricingTierSummaryResponse;
import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.domain.model.ModifierType;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionPricingSwaggerTest {

    @Test
    @DisplayName("CMS-01 SWG-01: CommissionPricingController declares @Tag, @Operation, and 200/500 responses with schema models")
    void controller_declaresCompleteOpenApiMetadata() throws NoSuchMethodException {
        Tag tag = CommissionPricingController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Commission Pricing");
        assertThat(tag.description()).isNotEmpty();

        Method method = CommissionPricingController.class.getMethod("getCommissionPricing");
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).isNotBlank();

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();

        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, Function.identity()));

        assertThat(responseMap.keySet()).contains("200", "500");

        ApiResponse okResponse = responseMap.get("200");
        assertThat(okResponse.content()).isNotEmpty();
        assertThat(okResponse.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(okResponse.content()[0].schema().implementation()).isEqualTo(PublicCommissionPricingResponse.class);

        ApiResponse serverErrorResponse = responseMap.get("500");
        assertThat(serverErrorResponse.content()).isNotEmpty();
        Content content = serverErrorResponse.content()[0];
        assertThat(content.mediaType()).isEqualTo("application/json");
        assertThat(content.schema().implementation()).isEqualTo(ErrorResponse.class);
        assertThat(content.examples()).isNotEmpty();
        assertThat(content.examples()[0].value()).contains("\"code\":");
    }

    @Test
    @DisplayName("CMS-01 SWG-02: PublicCommissionPricingResponse records and enums declare @Schema on types and fields")
    void responseDtosAndEnums_declareSchemaAnnotations() throws NoSuchFieldException {
        assertThat(SelectionType.class.getAnnotation(Schema.class)).isNotNull();
        assertThat(UiType.class.getAnnotation(Schema.class)).isNotNull();
        assertThat(ModifierType.class.getAnnotation(Schema.class)).isNotNull();

        assertSchemaOnRecordAndFields(
                PublicCommissionPricingResponse.class,
                "tiers", "rules"
        );

        assertSchemaOnRecordAndFields(
                PricingTierSummaryResponse.class,
                "tierId", "tierCode", "tierName", "description",
                "basePrice", "customCalculatedPrice", "savingsAmount",
                "maxRevisions", "isCustom", "displayOrder",
                "triggerOptionIds", "bundledOptionIds"
        );

        assertSchemaOnRecordAndFields(
                PricingRuleResponse.class,
                "configId", "ruleKey", "configName", "description",
                "selectionType", "uiType", "isBasePrice", "minQuantity", "maxQuantity",
                "isRequired", "isSystem", "displayOrder",
                "dependsOnOptionId", "dependsOnRuleKey", "dependsOnOptionCode", "options"
        );

        assertSchemaOnRecordAndFields(
                PricingOptionResponse.class,
                "optionId", "optionCode", "optionLabel", "modifierType", "basePrice", "isDefault", "displayOrder"
        );
    }

    private void assertSchemaOnRecordAndFields(Class<?> type, String... fields) throws NoSuchFieldException {
        Schema classSchema = type.getAnnotation(Schema.class);
        assertThat(classSchema)
                .withFailMessage("%s should have @Schema annotation", type.getSimpleName())
                .isNotNull();
        assertThat(classSchema.description())
                .withFailMessage("%s @Schema description should not be empty", type.getSimpleName())
                .isNotEmpty();

        for (String field : fields) {
            Schema fieldSchema = type.getDeclaredField(field).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("%s.%s should have @Schema annotation", type.getSimpleName(), field)
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("%s.%s @Schema description should not be empty", type.getSimpleName(), field)
                    .isNotEmpty();
        }
    }
}
