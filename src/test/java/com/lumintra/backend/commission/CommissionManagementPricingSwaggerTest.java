package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.controller.CommissionManagementPricingController;
import com.lumintra.backend.commission.api.request.CreatePricingOptionRequest;
import com.lumintra.backend.commission.api.request.CreatePricingRuleRequest;
import com.lumintra.backend.commission.api.response.BundledOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleSummaryResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierSummaryResponse;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionManagementPricingSwaggerTest {

    @Test
    @DisplayName("CMS-29/30 SWG-01: CommissionManagementPricingController declares @Tag, @SecurityRequirement, @PreAuthorize, and complete @Operation/@ApiResponses")
    void controller_declaresCompleteOpenApiMetadata() throws NoSuchMethodException {
        Tag tag = CommissionManagementPricingController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Commission Pricing Management");
        assertThat(tag.description()).isNotEmpty();

        SecurityRequirement secReq = CommissionManagementPricingController.class.getAnnotation(SecurityRequirement.class);
        assertThat(secReq).isNotNull();
        assertThat(secReq.name()).isEqualTo("bearerAuth");

        PreAuthorize preAuth = CommissionManagementPricingController.class.getAnnotation(PreAuthorize.class);
        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).contains("MANAGER", "ADMIN");

        Method summaryMethod = CommissionManagementPricingController.class.getMethod("getManagementPricing");
        assertMethodOpenApi(summaryMethod, "200", ManagementCommissionPricingResponse.class, "200", "401", "403", "500");

        Method tierDetailMethod = CommissionManagementPricingController.class.getMethod("getManagementPricingTierDetail", Long.class);
        assertMethodOpenApi(tierDetailMethod, "200", ManagementPricingTierResponse.class, "200", "400", "401", "403", "404", "500");

        Method ruleDetailMethod = CommissionManagementPricingController.class.getMethod("getManagementPricingRuleDetail", Long.class);
        assertMethodOpenApi(ruleDetailMethod, "200", ManagementPricingRuleResponse.class, "200", "400", "401", "403", "404", "500");

        Method createRuleMethod = CommissionManagementPricingController.class.getMethod("createCustomPricingRule", CreatePricingRuleRequest.class);
        assertMethodOpenApi(createRuleMethod, "201", ManagementPricingRuleResponse.class, "201", "400", "401", "403", "409", "500");
    }

    @Test
    @DisplayName("CMS-29/30 SWG-02: ManagementCommissionPricingResponse and request/response records declare @Schema on record and fields")
    void responseAndRequestDtos_declareSchemaAnnotations() throws NoSuchFieldException {
        assertSchemaOnRecordAndFields(
                ManagementCommissionPricingResponse.class,
                "tiers", "rules"
        );

        assertSchemaOnRecordAndFields(
                ManagementPricingTierSummaryResponse.class,
                "tierId", "tierCode", "tierName", "basePrice",
                "customCalculatedPrice", "savingsAmount", "maxRevisions",
                "isCustom", "isActive", "displayOrder", "triggerOptionIds", "includedOptionCount"
        );

        assertSchemaOnRecordAndFields(
                ManagementPricingRuleSummaryResponse.class,
                "configId", "configCode", "configName", "selectionType",
                "uiType", "isBasePrice", "minQuantity", "maxQuantity",
                "isRequired", "isSystem", "isActive",
                "displayOrder", "dependsOnOptionId", "optionCount"
        );

        assertSchemaOnRecordAndFields(
                ManagementPricingTierResponse.class,
                "tierId", "tierCode", "tierName", "description",
                "basePrice", "customCalculatedPrice", "savingsAmount", "discountPercent",
                "maxRevisions", "extraRevisionFeePercent", "isCustom", "isActive", "displayOrder",
                "triggerOptionIds", "includedOptions", "includedOptionIds"
        );

        assertSchemaOnRecordAndFields(
                BundledOptionResponse.class,
                "optionId", "ruleKey", "configName", "optionCode", "optionLabel", "modifierType", "basePrice"
        );

        assertSchemaOnRecordAndFields(
                ManagementPricingRuleResponse.class,
                "configId", "configCode", "configName", "description",
                "selectionType", "uiType", "isBasePrice", "minQuantity", "maxQuantity",
                "isRequired", "isSystem", "isActive", "displayOrder",
                "dependsOnOptionId", "dependsOnRuleKey", "dependsOnOptionCode", "options"
        );

        assertSchemaOnRecordAndFields(
                ManagementPricingOptionResponse.class,
                "optionId", "optionCode", "optionLabel", "modifierType", "basePrice", "isDefault", "isActive", "displayOrder"
        );

        assertSchemaOnRecordAndFields(
                CreatePricingRuleRequest.class,
                "configCode", "configName", "description",
                "selectionType", "uiType", "isBasePrice", "minQuantity", "maxQuantity",
                "isRequired", "displayOrder", "dependsOnOptionId", "options"
        );

        assertSchemaOnRecordAndFields(
                CreatePricingOptionRequest.class,
                "optionCode", "optionLabel", "optionValue", "modifierType", "basePrice", "isDefault", "displayOrder"
        );
    }

    private void assertMethodOpenApi(Method method, String successCode, Class<?> successSchema, String... expectedCodes) {
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).isNotBlank();

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();

        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, Function.identity()));

        assertThat(responseMap.keySet()).contains(expectedCodes);

        ApiResponse okResponse = responseMap.get(successCode);
        assertThat(okResponse.content()).isNotEmpty();
        assertThat(okResponse.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(okResponse.content()[0].schema().implementation()).isEqualTo(successSchema);

        ApiResponse serverErrorResponse = responseMap.get("500");
        assertThat(serverErrorResponse.content()).isNotEmpty();
        Content serverErrorContent = serverErrorResponse.content()[0];
        assertThat(serverErrorContent.schema().implementation()).isEqualTo(ErrorResponse.class);
    }

    private void assertSchemaOnRecordAndFields(Class<?> type, String... fields) throws NoSuchFieldException {
        Schema classSchema = type.getAnnotation(Schema.class);
        assertThat(classSchema)
                .withFailMessage("%s should have @Schema annotation", type.getSimpleName())
                .isNotNull();
        assertThat(classSchema.description())
                .withFailMessage("%s @Schema description should not be empty", type.getSimpleName())
                .isNotBlank();

        for (String fieldName : fields) {
            java.lang.reflect.Field field = type.getDeclaredField(fieldName);
            Schema fieldSchema = field.getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Field %s in %s should have @Schema annotation", fieldName, type.getSimpleName())
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("Field %s in %s @Schema description should not be empty", fieldName, type.getSimpleName())
                    .isNotBlank();
        }
    }
}
