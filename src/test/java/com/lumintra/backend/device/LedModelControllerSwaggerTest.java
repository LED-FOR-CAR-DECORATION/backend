package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.ManagedLedModelController;
import com.lumintra.backend.device.api.request.LedModelFilterRequest;
import com.lumintra.backend.device.api.response.LedModelListResponse;
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

class LedModelControllerSwaggerTest {

    @Test
    @DisplayName("SWAGGER-01: ManagedLedModelController has @Tag annotation")
    void controller_hasTagAnnotation() {
        Tag tag = ManagedLedModelController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("LED Hardware Catalog");
        assertThat(tag.description()).isNotEmpty();
    }

    @Test
    @DisplayName("SWAGGER-02: listLedModels method has @Operation, @SecurityRequirement, and complete @ApiResponses")
    void listLedModels_hasOpenApiAnnotations() throws NoSuchMethodException {
        Method method = ManagedLedModelController.class.getMethod(
                "listLedModels", Authentication.class, LedModelFilterRequest.class);

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
    @DisplayName("SWAGGER-03: LedModelFilterRequest and LedModelListResponse records and fields declare @Schema")
    void requestAndResponseDtos_haveSchemaAnnotations() throws NoSuchFieldException {
        assertSchemaOnRecordAndFields(
                LedModelFilterRequest.class,
                "page", "size", "status", "widthPx", "heightPx", "unlinkedToProductOnly", "lowStockOnly", "keyword"
        );
        assertSchemaOnRecordAndFields(LedModelListResponse.class, "items", "meta");
        assertSchemaOnRecordAndFields(
                LedModelListResponse.LedModelItem.class,
                "modelId", "productId", "modelName", "stockQuantity", "isLowStock", "status", "spec"
        );
        assertSchemaOnRecordAndFields(LedModelListResponse.LedSpec.class, "widthPx", "heightPx", "attributes");
        assertSchemaOnRecordAndFields(LedModelListResponse.SpecAttribute.class, "label", "value");
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
