package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.request.AhamoveWebhookRequest;
import com.lumintra.backend.commerce.api.response.AhamoveWebhookResponse;
import com.lumintra.backend.commerce.application.ProcessAhamoveWebhookUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Commerce Webhooks", description = "Webhook endpoints for external delivery and payment callbacks")
@RestController
@RequestMapping("/api/commerce/webhooks")
@RequiredArgsConstructor
public class AhamoveWebhookController {

    private final ProcessAhamoveWebhookUseCase processAhamoveWebhookUseCase;

    @Operation(
            summary = "Process AhaMove delivery status webhook",
            description = "Public webhook endpoint authenticated via configurable AhaMove webhook token (API-Key header or Authorization Bearer token). Atomically, idempotently, and monotonically synchronizes AhaMove order lifecycle updates into order_delivery.delivery_status and orders.status."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Webhook accepted (processed, ignored as idempotent/terminal, or unmatched tracking number)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: malformed JSON payload or missing required webhook fields (_id, status)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request payload",
                                              "status": 400,
                                              "timestamp": "2026-11-12T09:00:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "orderId",
                                                  "message": "_id must not be blank"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing or invalid webhook authentication header/token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping("/ahamove")
    public ResponseEntity<ApiResponse<AhamoveWebhookResponse>> handleAhamoveWebhook(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody AhamoveWebhookRequest request) {
        AhamoveWebhookResponse response = processAhamoveWebhookUseCase.execute(headers, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
