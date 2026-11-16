package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.controller.CommissionController;
import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionControllerSwaggerTest {

    @Test
    @DisplayName("CMS-04 SWG-01: CommissionController declares @Tag, @PreAuthorize, @Operation, and 200/400/401/403/500 responses")
    void controller_declaresCompleteOpenApiMetadata() throws NoSuchMethodException {
        Tag tag = CommissionController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Customer Commissions");
        assertThat(tag.description()).isNotEmpty();

        Method method = CommissionController.class.getMethod(
                "listMyCommissions", Authentication.class, CustomerCommissionFilterRequest.class
        );

        PreAuthorize preAuth = method.getAnnotation(PreAuthorize.class);
        assertThat(preAuth).isNotNull();
        assertThat(preAuth.value()).isEqualTo("hasRole('CUSTOMER')");

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).isNotBlank();

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();

        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, Function.identity()));

        assertThat(responseMap.keySet()).contains("200", "400", "401", "403", "500");
    }

    @Test
    @DisplayName("CMS-04 SWG-02: Filter request, list response, summary item, and CommissionStatus declare @Schema")
    void dtosAndEnums_declareSchemaAnnotations() throws NoSuchFieldException {
        assertThat(CommissionStatus.class.getAnnotation(Schema.class)).isNotNull();

        assertSchemaOnRecordAndFields(
                CustomerCommissionFilterRequest.class,
                "status", "sortDirection", "page", "size"
        );

        assertSchemaOnRecordAndFields(
                CustomerCommissionListResponse.class,
                "items", "meta"
        );

        assertSchemaOnRecordAndFields(
                CustomerCommissionSummaryItem.class,
                "commissionId", "title", "tierCode", "targetMatrixDimensions",
                "status", "initialPrice", "extraRevisionFee", "totalPrice",
                "revisionsUsed", "maxRevisions", "activePaymentCode",
                "quoteExpiresAt", "createdAt", "updatedAt"
        );
    }

    private void assertSchemaOnRecordAndFields(Class<?> type, String... fields) throws NoSuchFieldException {
        Schema classSchema = type.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();
        assertThat(classSchema.description()).isNotBlank();

        for (String field : fields) {
            Schema fieldSchema = type.getDeclaredField(field).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("%s.%s should have @Schema", type.getSimpleName(), field)
                    .isNotNull();
            assertThat(fieldSchema.description()).isNotBlank();
        }
    }
}
