package com.lumintra.backend.device;

import com.lumintra.backend.device.api.controller.DeviceController;
import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceControllerSwaggerTest {

    @Test
    @DisplayName("DEV-93 SWG-01: DeviceController.claimDevice declares @Tag, @Operation, bearerAuth security, and 200/400/401/403/404/409/500 responses")
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
        Set<String> codes = Arrays.stream(apiResponses.value())
                .map(ApiResponse::responseCode)
                .collect(Collectors.toSet());
        assertThat(codes).containsExactlyInAnyOrder("200", "400", "401", "403", "404", "409", "500");

        assertThat(ClaimDeviceRequest.class.getAnnotation(Schema.class)).isNotNull();
        assertThat(ClaimDeviceResponse.class.getAnnotation(Schema.class)).isNotNull();
    }
}
