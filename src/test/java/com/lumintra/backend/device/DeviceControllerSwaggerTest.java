package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceControllerSwaggerTest {

    @Test
    @DisplayName("DEV-93 SWG-01: DeviceController.claimDevice declares @Tag, @Operation, bearerAuth security, and 200/400/401/403/404/409/500 responses with example objects")
    void claimDevice_hasCompleteOpenApiMetadata() {
        Tag tag = DeviceController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Customer Devices");

        Method method = Arrays.stream(DeviceController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("claimDevice"))
                .findFirst()
                .orElseThrow();

        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).contains("HMAC-SHA256");
        assertThat(operation.security()).isNotEmpty();
        assertThat(operation.security()[0].name()).isEqualTo("bearerAuth");

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();
        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, Function.identity()));

        assertThat(responseMap.keySet()).containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409", "500");

        ApiResponse okResponse = responseMap.get("200");
        assertThat(okResponse.content()).isNotEmpty();
        assertThat(okResponse.content()[0].mediaType()).isEqualTo("application/json");
        assertThat(okResponse.content()[0].schema().implementation()).isEqualTo(ClaimDeviceResponse.class);

        for (String errCode : new String[]{"400", "401", "403", "404", "409", "500"}) {
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
    @DisplayName("DEV-93 SWG-02: ClaimDeviceRequest and ClaimDeviceResponse records declare @Schema on record and fields")
    void requestAndResponseDtos_haveSchemaAnnotations() throws NoSuchFieldException {
        assertSchemaOnRecordAndFields(
                ClaimDeviceRequest.class,
                "qrPayload", "deviceName"
        );
        assertSchemaOnRecordAndFields(
                ClaimDeviceResponse.class,
                "deviceId", "serialNumber", "macAddress", "deviceName",
                "modelId", "modelName", "spec", "boundAt", "warrantyExpiresAt"
        );
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
