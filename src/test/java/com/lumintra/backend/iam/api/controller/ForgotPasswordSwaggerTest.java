package com.lumintra.backend.iam.api.controller;
import com.lumintra.backend.iam.api.request.ForgotPasswordRequest;
import com.lumintra.backend.iam.api.response.ForgotPasswordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
class ForgotPasswordSwaggerTest {
    @Test @DisplayName("N: Endpoint documents success and applicable error contracts") void endpoint() throws Exception {
        assertThat(ForgotPasswordController.class.getAnnotation(Tag.class)).isNotNull();
        var method = ForgotPasswordController.class.getMethod("forgotPassword", ForgotPasswordRequest.class);
        assertThat(method.getAnnotation(Operation.class)).isNotNull();
        assertThat(method.getAnnotation(ApiResponses.class).value()).extracting(r -> r.responseCode()).containsExactlyInAnyOrder("200", "400", "429", "503", "500");
    }
    @Test @DisplayName("N: Request and response fields have schema descriptions and examples") void dtos() {
        for (Class<?> dto : new Class[]{ForgotPasswordRequest.class, ForgotPasswordResponse.class}) {
            assertThat(dto.getAnnotation(Schema.class)).isNotNull();
            for (var field : dto.getDeclaredFields()) { assertThat(field.getAnnotation(Schema.class).description()).isNotBlank(); assertThat(field.getAnnotation(Schema.class).example()).isNotBlank(); }
        }
    }
}
