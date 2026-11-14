package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedLedInstanceController;
import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
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
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LedInstanceControllerSwaggerTest {

    @Test
    @DisplayName("SWAGGER-01: ManagedLedInstanceController declares @Tag annotation")
    void controller_hasTagAnnotation() {
        Tag tag = ManagedLedInstanceController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("LED Hardware Inventory");
        assertThat(tag.description()).isNotEmpty();
    }

    @Test
    @DisplayName("SWAGGER-02: listLedInstances declares @Operation, @SecurityRequirement, and complete @ApiResponses")
    void listLedInstances_hasOpenApiAnnotations() throws NoSuchMethodException {
        Method method = ManagedLedInstanceController.class.getMethod(
                "listLedInstances", Authentication.class, LedInstanceFilterRequest.class);

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotEmpty();
        assertThat(operation.description()).isNotEmpty();

        SecurityRequirement securityRequirement = method.getAnnotation(SecurityRequirement.class);
        assertThat(securityRequirement).isNotNull();
        assertThat(securityRequirement.name()).isEqualTo("bearerAuth");

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();
        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, r -> r));

        assertThat(responseMap).containsKeys("200", "400", "401", "403", "500");

        for (String errCode : new String[]{"400", "401", "403", "500"}) {
            ApiResponse errResponse = responseMap.get(errCode);
            assertThat(errResponse.content()).isNotEmpty();
            Content content = errResponse.content()[0];
            assertThat(content.mediaType()).isEqualTo("application/json");
            assertThat(content.schema().implementation()).isEqualTo(ErrorResponse.class);
            assertThat(content.examples()).isNotEmpty();
            assertThat(content.examples()[0].value()).contains("\"code\":");
        }
    }

    @Test
    @DisplayName("SWAGGER-03: LedInstanceFilterRequest and LedInstanceListResponse records declare @Schema")
    void requestAndResponseDtos_haveSchemaAnnotations() throws NoSuchFieldException {
        assertSchemaOnRecordAndFields(
                LedInstanceFilterRequest.class,
                "page", "size", "modelId", "status", "search"
        );
        assertSchemaOnRecordAndFields(LedInstanceListResponse.class, "items", "meta");
        assertSchemaOnRecordAndFields(
                LedInstanceListResponse.LedInstanceItem.class,
                "instanceId", "modelId", "modelName", "serialNumber", "macAddress",
                "hardwareRevision", "deviceName", "status", "fulfillment", "owner",
                "boundAt", "warrantyExpiresAt", "manufacturedAt", "qrPayload", "createdAt", "updatedAt"
        );
        assertSchemaOnRecordAndFields(LedInstanceListResponse.FulfillmentInfo.class, "orderId", "orderItemId");
        assertSchemaOnRecordAndFields(
                LedInstanceListResponse.OwnerInfo.class,
                "customerId", "fullName", "phone", "email"
        );
    }

    @Test
    @DisplayName("SWAGGER-04: getLedInstanceDetail declares @Operation, @SecurityRequirement, and complete @ApiResponses")
    void getLedInstanceDetail_hasOpenApiAnnotations() throws NoSuchMethodException {
        Method method = ManagedLedInstanceController.class.getMethod(
                "getLedInstanceDetail", Authentication.class, Long.class);

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotEmpty();
        assertThat(operation.description()).isNotEmpty();

        SecurityRequirement securityRequirement = method.getAnnotation(SecurityRequirement.class);
        assertThat(securityRequirement).isNotNull();
        assertThat(securityRequirement.name()).isEqualTo("bearerAuth");

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();
        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, r -> r));

        assertThat(responseMap).containsKeys("200", "400", "401", "403", "404", "500");

        for (String errCode : new String[]{"400", "401", "403", "404", "500"}) {
            ApiResponse errResponse = responseMap.get(errCode);
            assertThat(errResponse.content()).isNotEmpty();
            Content content = errResponse.content()[0];
            assertThat(content.mediaType()).isEqualTo("application/json");
            assertThat(content.schema().implementation()).isEqualTo(ErrorResponse.class);
            assertThat(content.examples()).isNotEmpty();
            assertThat(content.examples()[0].value()).contains("\"code\":");
        }
    }

    @Test
    @DisplayName("SWAGGER-05: LedInstanceDetailResponse, nested records, and WarrantyStatus declare @Schema")
    void detailResponseDtos_haveSchemaAnnotations() throws NoSuchFieldException {
        assertSchemaOnRecordAndFields(
                LedInstanceDetailResponse.class,
                "instanceId", "model", "serialNumber", "macAddress", "hardwareRevision",
                "currentFirmwareId", "deviceName", "status", "warrantyStatus",
                "boundAt", "warrantyExpiresAt", "manufacturedAt", "fulfillment", "owner",
                "qrPayload", "createdAt", "updatedAt"
        );
        assertSchemaOnRecordAndFields(
                LedInstanceDetailResponse.ModelInfo.class,
                "modelId", "productId", "modelName", "status", "spec"
        );
        assertSchemaOnRecordAndFields(LedInstanceDetailResponse.FulfillmentInfo.class, "orderId", "orderItemId");
        assertSchemaOnRecordAndFields(
                LedInstanceDetailResponse.OwnerInfo.class,
                "customerId", "fullName", "phone", "email"
        );

        Schema warrantySchema = WarrantyStatus.class.getAnnotation(Schema.class);
        assertThat(warrantySchema).isNotNull();
        assertThat(warrantySchema.description()).isNotEmpty();
    }

    private void assertSchemaOnRecordAndFields(Class<?> type, String... fields) throws NoSuchFieldException {
        Schema classSchema = type.getAnnotation(Schema.class);
        assertThat(classSchema)
                .withFailMessage("%s should have @Schema", type.getSimpleName())
                .isNotNull();
        assertThat(classSchema.description()).isNotEmpty();

        for (String field : fields) {
            Schema fieldSchema = type.getDeclaredField(field).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("%s.%s should have @Schema", type.getSimpleName(), field)
                    .isNotNull();
            assertThat(fieldSchema.description()).isNotEmpty();
        }
    }
}
