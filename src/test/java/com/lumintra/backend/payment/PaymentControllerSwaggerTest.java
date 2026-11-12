package com.lumintra.backend.payment;

import com.lumintra.backend.payment.api.controller.PaymentController;
import com.lumintra.backend.payment.api.request.SePayWebhookRequest;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.api.response.SePayWebhookAckResponse;
import com.lumintra.backend.payment.api.response.SePayWebhookResponse;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentControllerSwaggerTest {

    @Test
    @DisplayName("SWAGGER-01: PaymentController class is annotated with @Tag for Payment")
    void paymentController_hasTagAnnotation() {
        Tag tag = PaymentController.class.getAnnotation(Tag.class);
        assertThat(tag).isNotNull();
        assertThat(tag.name()).isEqualTo("Payment");
        assertThat(tag.description()).contains("SePay VietQR");
    }

    @Test
    @DisplayName("SWAGGER-02: getPaymentSession method is annotated with @Operation")
    void getPaymentSession_hasOperationAnnotation() throws NoSuchMethodException {
        Method method = PaymentController.class.getMethod("getPaymentSession", Authentication.class, String.class);
        Operation operation = method.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Get SePay payment session and poll status");
        assertThat(operation.description()).contains("SePay VietQR payment session details");
    }

    @Test
    @DisplayName("SWAGGER-03: getPaymentSession method documents 200, 400, 401, 403, 404, and 500 @ApiResponses")
    void getPaymentSession_hasCompleteApiResponses() throws NoSuchMethodException {
        Method method = PaymentController.class.getMethod("getPaymentSession", Authentication.class, String.class);
        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, r -> r));

        assertThat(responseMap).containsKeys("200", "400", "401", "403", "404", "500");

        assertThat(responseMap.get("200").description()).contains("successfully");

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
    @DisplayName("SWAGGER-04: PaymentSessionResponse record and all fields have @Schema annotations")
    void paymentSessionResponse_hasSchemaAnnotationsOnAllFields() throws NoSuchFieldException {
        Schema classSchema = PaymentSessionResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();
        assertThat(classSchema.description()).isNotEmpty();

        String[] fields = {
                "paymentId", "paymentCode", "targetType", "targetId", "gateway",
                "qrUrl", "bankName", "accountNumber", "accountName", "amount",
                "status", "expiresAt", "paidAt", "createdAt"
        };

        for (String fieldName : fields) {
            Schema fieldSchema = PaymentSessionResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Field '%s' should have @Schema annotation", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("Field '%s' should have non-empty description", fieldName)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("SWAGGER-05: handleSePayWebhook method is annotated with @Operation")
    void handleSePayWebhook_hasOperationAnnotation() throws NoSuchMethodException {
        Method method = PaymentController.class.getMethod("handleSePayWebhook", HttpHeaders.class, SePayWebhookRequest.class);
        Operation operation = method.getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.summary()).isEqualTo("Process SePay payment webhook");
        assertThat(operation.description()).contains("Public webhook endpoint authenticated via the Apikey header");
    }

    @Test
    @DisplayName("SWAGGER-06: handleSePayWebhook method documents 200, 400, 401, and 500 @ApiResponses")
    void handleSePayWebhook_hasCompleteApiResponses() throws NoSuchMethodException {
        Method method = PaymentController.class.getMethod("handleSePayWebhook", HttpHeaders.class, SePayWebhookRequest.class);
        ApiResponses apiResponses = method.getAnnotation(ApiResponses.class);

        assertThat(apiResponses).isNotNull();
        Map<String, ApiResponse> responseMap = Arrays.stream(apiResponses.value())
                .collect(Collectors.toMap(ApiResponse::responseCode, r -> r));

        assertThat(responseMap).containsKeys("200", "400", "401", "500");
        assertThat(responseMap.get("200").description()).contains("Webhook accepted");

        for (String errCode : new String[]{"400", "401", "500"}) {
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
    @DisplayName("SWAGGER-07: SePayWebhookRequest record and all fields have @Schema annotations")
    void sePayWebhookRequest_hasSchemaAnnotationsOnAllFields() throws NoSuchFieldException {
        Schema classSchema = SePayWebhookRequest.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();
        assertThat(classSchema.description()).isNotEmpty();

        String[] fields = {
                "id", "gateway", "transactionDate", "accountNumber", "code",
                "content", "transferType", "transferAmount", "accumulated",
                "subAccount", "referenceCode", "description"
        };

        for (String fieldName : fields) {
            Schema fieldSchema = SePayWebhookRequest.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Field '%s' should have @Schema annotation in SePayWebhookRequest", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("Field '%s' should have non-empty description in SePayWebhookRequest", fieldName)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("SWAGGER-08: SePayWebhookResponse record and all fields have @Schema annotations")
    void sePayWebhookResponse_hasSchemaAnnotationsOnAllFields() throws NoSuchFieldException {
        Schema classSchema = SePayWebhookResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();
        assertThat(classSchema.description()).isNotEmpty();

        String[] fields = {
                "sepayTxId", "paymentId", "paymentCode", "targetType",
                "targetId", "status", "outcome", "message"
        };

        for (String fieldName : fields) {
            Schema fieldSchema = SePayWebhookResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Field '%s' should have @Schema annotation in SePayWebhookResponse", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("Field '%s' should have non-empty description in SePayWebhookResponse", fieldName)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("SWAGGER-09: SePayWebhookAckResponse record and all fields have @Schema annotations")
    void sePayWebhookAckResponse_hasSchemaAnnotationsOnAllFields() throws NoSuchFieldException {
        Schema classSchema = SePayWebhookAckResponse.class.getAnnotation(Schema.class);
        assertThat(classSchema).isNotNull();
        assertThat(classSchema.description()).isNotEmpty();

        String[] fields = {"success", "data"};

        for (String fieldName : fields) {
            Schema fieldSchema = SePayWebhookAckResponse.class.getDeclaredField(fieldName).getAnnotation(Schema.class);
            assertThat(fieldSchema)
                    .withFailMessage("Field '%s' should have @Schema annotation in SePayWebhookAckResponse", fieldName)
                    .isNotNull();
            assertThat(fieldSchema.description())
                    .withFailMessage("Field '%s' should have non-empty description in SePayWebhookAckResponse", fieldName)
                    .isNotEmpty();
        }
    }
}
