package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.ResetPasswordRequest;
import com.lumintra.backend.iam.api.response.ResetPasswordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordSwaggerTest {

    @Test
    @DisplayName("UTCID01 [N]: Endpoint documents Tag, Operation, and all required ApiResponses")
    void verifyEndpointAnnotations() throws Exception {
        Tag tag = ResetPasswordController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Authentication");

        Method method = ResetPasswordController.class.getMethod("resetPassword", ResetPasswordRequest.class);
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isNotBlank();
        assertThat(operation.description()).isNotBlank();

        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);
        assertThat(apiResponses).isNotNull();
        assertThat(apiResponses.value())
                .extracting(r -> r.responseCode())
                .containsExactlyInAnyOrder("200", "400", "404", "429", "503", "500");
    }

    @Test
    @DisplayName("UTCID02 [N]: Request and response fields have schema descriptions and examples")
    void verifyDtoSchemas() {
        for (Class<?> dto : new Class<?>[]{ResetPasswordRequest.class, ResetPasswordResponse.class}) {
            Schema classSchema = dto.getAnnotation(Schema.class);
            assertThat(classSchema).isNotNull();
            assertThat(classSchema.description()).isNotBlank();

            for (var field : dto.getDeclaredFields()) {
                Schema fieldSchema = field.getAnnotation(Schema.class);
                if (fieldSchema != null) {
                    assertThat(fieldSchema.description()).isNotBlank();
                    assertThat(fieldSchema.example()).isNotBlank();
                }
            }
        }
    }
}
