package com.lumintra.backend.iam.api.controller;

import com.lumintra.backend.iam.api.request.RegisterRequest;
import com.lumintra.backend.iam.api.response.RegisterResponse;
import com.lumintra.backend.shared.config.OpenApiConfig;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerSwaggerTest {

    @Test
    @DisplayName("AuthController should have Swagger @Tag annotation")
    void authControllerShouldHaveTagAnnotation() {
        Tag tag = AuthController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Authentication");
        assertThat(tag.description()).isNotEmpty();
    }

    @Test
    @DisplayName("register endpoint should have Swagger @Operation and documentation details")
    void registerEndpointShouldHaveOperationAnnotation() throws NoSuchMethodException {
        Method registerMethod = AuthController.class.getMethod("register", RegisterRequest.class);
        Operation operation = registerMethod.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Register a new customer account");
        assertThat(operation.description()).contains("Registers a new customer account");
    }

    @Test
    @DisplayName("register endpoint should have documented @ApiResponses for 201, 400, 409, and 500")
    void registerEndpointShouldHaveDocumentedApiResponses() throws NoSuchMethodException {
        Method registerMethod = AuthController.class.getMethod("register", RegisterRequest.class);
        ApiResponses apiResponses = registerMethod.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, io.swagger.v3.oas.annotations.responses.ApiResponse> responseMap =
                Arrays.stream(apiResponses.value())
                        .collect(Collectors.toMap(
                                io.swagger.v3.oas.annotations.responses.ApiResponse::responseCode,
                                r -> r
                        ));

        assertThat(responseMap).containsKeys("201", "400", "409", "500");
        assertThat(responseMap.get("201").description()).contains("successfully");
        assertThat(responseMap.get("400").description()).contains("Validation failed");
        assertThat(responseMap.get("409").description()).contains("already registered");
        assertThat(responseMap.get("500").description()).contains("Internal server error");
    }

    @Test
    @DisplayName("RegisterRequest record should document fields with @Schema annotations")
    void registerRequestShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = RegisterRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"email", "password", "confirmPassword"};
        for (String fieldName : fields) {
            Schema schema = RegisterRequest.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(schema)
                    .withFailMessage("Missing @Schema on record field %s", fieldName)
                    .isNotNull();
            assertThat(schema.description()).isNotEmpty();
            assertThat(schema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("RegisterResponse class should document fields with @Schema annotations")
    void registerResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = RegisterResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"id", "email", "accountType", "status"};
        for (String fieldName : fields) {
            Schema fieldSchema = RegisterResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Missing @Schema on field %s", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description()).isNotEmpty();
            assertThat(fieldSchema.example()).isNotEmpty();
        }
    }

    @Test
    @DisplayName("ApiResponse envelope should have @Schema annotations")
    void apiResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = ApiResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        assertThat(ApiResponse.class.getDeclaredField("data").getAnnotation(Schema.class)).isNotNull();
        assertThat(ApiResponse.class.getDeclaredField("meta").getAnnotation(Schema.class)).isNotNull();
    }

    @Test
    @DisplayName("ErrorResponse envelope should have @Schema annotations")
    void errorResponseShouldHaveSchemaAnnotations() throws NoSuchFieldException {
        Schema classSchema = ErrorResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();

        String[] fields = {"code", "message", "status", "timestamp", "traceId", "details"};
        for (String fieldName : fields) {
            assertThat(ErrorResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("OpenApiConfig should produce OpenAPI bean with title and version")
    void openApiConfigShouldProduceValidMetadata() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("Lumintra Backend API");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
    }
}
