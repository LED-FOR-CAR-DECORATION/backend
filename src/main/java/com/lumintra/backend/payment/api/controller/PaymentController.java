package com.lumintra.backend.payment.api.controller;

import com.lumintra.backend.payment.api.request.SePayWebhookRequest;
import com.lumintra.backend.payment.api.response.PaymentSessionResponse;
import com.lumintra.backend.payment.api.response.SePayWebhookAckResponse;
import com.lumintra.backend.payment.api.response.SePayWebhookResponse;
import com.lumintra.backend.payment.application.GetPaymentSessionUseCase;
import com.lumintra.backend.payment.application.MockPaymentSuccessUseCase;
import com.lumintra.backend.payment.application.ProcessSePayWebhookUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "SePay VietQR payment session retrieval, live status polling, and webhook APIs")
public class PaymentController {

    private final GetPaymentSessionUseCase getPaymentSessionUseCase;
    private final ProcessSePayWebhookUseCase processSePayWebhookUseCase;
    private final MockPaymentSuccessUseCase mockPaymentSuccessUseCase;

    @Operation(
            summary = "Get SePay payment session and poll status",
            description = "Retrieves the SePay VietQR payment session details (QR URL, bank details, amount, expiration) and current status for 3-second frontend polling. Restricted to the owning CUSTOMER account."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Payment session retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request: blank or invalid paymentCode parameter",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                     name = "Validation Failed",
                                    value = """
                                            {
                                              "code": "VALIDATION_FAILED",
                                              "message": "Invalid request parameter",
                                              "status": 400,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "paymentCode",
                                                  "message": "Payment code must not be blank"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized Error",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: account is not CUSTOMER or does not own the payment session",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Forbidden Error",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Payment session not found",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Payment Session Not Found",
                                    value = """
                                            {
                                              "code": "PAYMENT_SESSION_NOT_FOUND",
                                              "message": "Payment session not found.",
                                              "status": 404,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Internal Server Error",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/{paymentCode}")
    public ResponseEntity<ApiResponse<PaymentSessionResponse>> getPaymentSession(
            Authentication authentication,
            @Parameter(description = "Unique SePay transfer payment code", example = "LUMORD102A1B2C3D4", required = true)
            @PathVariable("paymentCode")
            @NotBlank(message = "Payment code must not be blank")
            @Size(max = 100, message = "Payment code must not exceed 100 characters")
            String paymentCode) {
        Long customerId = extractCustomerId(authentication);
        PaymentSessionResponse response = getPaymentSessionUseCase.execute(customerId, paymentCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
            summary = "Process SePay payment webhook",
            description = "Public webhook endpoint authenticated via the Apikey header (Authorization: Apikey <key> or Apikey header). Enforces idempotency via sepay_tx_id, atomically marks the matching pending payment transaction SUCCEEDED, and publishes PaymentSucceededEvent."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Webhook accepted (processed, ignored as duplicate/non-pending/underpaid, or unmatched transfer)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request: malformed JSON payload or missing required webhook fields (id, transferType, transferAmount)",
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
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "id",
                                                  "message": "id must not be null"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing or invalid SePay Apikey webhook credential",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized Error",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Internal Server Error",
                                    value = """
                                            {
                                              "code": "INTERNAL_SERVER_ERROR",
                                              "message": "An unexpected error occurred",
                                              "status": 500,
                                              "timestamp": "2026-11-12T16:45:00Z",
                                              "traceId": "trace-12345",
                                              "details": []
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/webhooks/sepay")
    public ResponseEntity<SePayWebhookAckResponse> handleSePayWebhook(
            @RequestHeader HttpHeaders headers,
            @Valid @RequestBody SePayWebhookRequest request) {
        SePayWebhookResponse response = processSePayWebhookUseCase.execute(headers, request);
        return ResponseEntity.ok(SePayWebhookAckResponse.ok(response));
    }

    @Operation(
            summary = "Simulate mock payment success (development/testing)",
            description = "Simulates successful SePay payment for a given payment code without requiring actual bank transfer. Atomically marks transaction as SUCCEEDED and triggers PaymentSucceededEvent."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Mock payment succeeded"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad request: blank or invalid paymentCode parameter",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Payment session not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/mock-success/{paymentCode}")
    public ResponseEntity<ApiResponse<PaymentSessionResponse>> mockPaymentSuccess(
            @Parameter(description = "Unique SePay transfer payment code", example = "LUMORD102A1B2C3D4", required = true)
            @PathVariable("paymentCode")
            @NotBlank(message = "Payment code must not be blank")
            @Size(max = 100, message = "Payment code must not exceed 100 characters")
            String paymentCode) {
        PaymentSessionResponse response = mockPaymentSuccessUseCase.execute(paymentCode);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long extractCustomerId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new InvalidCredentialsException("Authentication is required.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid authenticated user identifier.");
        }
    }
}
