package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.controller.CommissionController;
import com.lumintra.backend.commission.api.request.CustomerCommissionFilterRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.CommissionAttachmentItemRequest;
import com.lumintra.backend.commission.api.request.SubmitCommissionRequest.SelectedOptionItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse.CustomerCommissionAttachmentItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionDetailResponse.CustomerDeliveredPreviewItem;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse;
import com.lumintra.backend.commission.api.response.CustomerCommissionListResponse.CustomerCommissionSummaryItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
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
    @DisplayName("CMS-04/CMS-05 SWG-01: CommissionController declares @Tag, @PreAuthorize, @Operation, and documented responses on list and detail endpoints")
    void controller_declaresCompleteOpenApiMetadata() throws NoSuchMethodException {
        Tag tag = CommissionController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Customer Commissions");
        assertThat(tag.description()).isNotEmpty();

        Method listMethod = CommissionController.class.getMethod(
                "listMyCommissions", Authentication.class, CustomerCommissionFilterRequest.class
        );
        assertEndpointMetadata(listMethod, "200", "400", "401", "403", "500");

        Method detailMethod = CommissionController.class.getMethod(
                "getMyCommissionDetail", Authentication.class, Long.class
        );
        assertEndpointMetadata(detailMethod, "200", "400", "401", "403", "404", "500");

        Method submitMethod = CommissionController.class.getMethod(
                "submitCommission", Authentication.class, SubmitCommissionRequest.class
        );
        assertEndpointMetadata(submitMethod, "201", "400", "401", "403", "404", "500");
    }

    @Test
    @DisplayName("CMS-03/04/05 SWG-02: Request/Filter/Response records and enums declare @Schema")
    void dtosAndEnums_declareSchemaAnnotations() throws NoSuchFieldException {
        assertThat(CommissionStatus.class.getAnnotation(Schema.class)).isNotNull();
        assertThat(CommissionSubmissionStatus.class.getAnnotation(Schema.class)).isNotNull();

        assertSchemaOnRecordAndFields(
                SubmitCommissionRequest.class,
                "title", "description", "tierCode", "selectedOptions", "attachments", "openPaymentImmediately"
        );

        assertSchemaOnRecordAndFields(
                SelectedOptionItem.class,
                "configId", "optionIds", "quantity", "textValue"
        );

        assertSchemaOnRecordAndFields(
                CommissionAttachmentItemRequest.class,
                "imageUrl", "caption"
        );

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

        assertSchemaOnRecordAndFields(
                CustomerCommissionDetailResponse.class,
                "commissionId", "customerId", "title", "description",
                "tierCode", "targetMatrixDimensions", "priceBreakdownJson",
                "initialPrice", "extraRevisionFee", "totalPrice",
                "revisionsUsed", "maxRevisions", "status", "activePaymentCode",
                "approvedSubmissionId", "rejectionReason", "chatActive",
                "quoteExpiresAt", "slaDueAt", "autoCompleteAt",
                "createdAt", "updatedAt", "attachments", "deliveredPreviews"
        );

        assertSchemaOnRecordAndFields(
                CustomerCommissionAttachmentItem.class,
                "attachmentId", "imageUrl", "caption", "displayOrder", "createdAt"
        );

        assertSchemaOnRecordAndFields(
                CustomerDeliveredPreviewItem.class,
                "submissionId", "versionNumber", "imageUrl", "designerNote",
                "status", "deliveredAt", "createdAt"
        );
    }

    private void assertEndpointMetadata(Method method, String... expectedCodes) {
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

        assertThat(responseMap.keySet()).contains(expectedCodes);
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
