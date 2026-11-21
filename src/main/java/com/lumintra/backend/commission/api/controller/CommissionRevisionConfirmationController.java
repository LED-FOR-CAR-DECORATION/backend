package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.ConfirmRevisionCardRequest;
import com.lumintra.backend.commission.api.response.ConfirmRevisionCardResponse;
import com.lumintra.backend.commission.application.ConfirmRevisionCardUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Commission Revision Cards", description = "Confirm customer revision briefs")
public class CommissionRevisionConfirmationController {
    private final ConfirmRevisionCardUseCase useCase;

    @PostMapping("/api/commissions/{id}/revision-card/confirm")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Confirm revision brief card",
            description =
                    "The owning customer confirms the latest revision card. Free revisions consume"
                        + " quota immediately; paid revisions return a paymentSession for 10% of"
                        + " initialPrice and await payment.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description =
                        "Revision confirmed; data contains commission state and optional"
                                + " PaymentSessionResponse"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid commission or message ID",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Validation"
                                                            + " failed\",\"status\":400,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Authentication required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"Unauthorized\",\"status\":401,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Owning CUSTOMER required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"COMMISSION_OWNERSHIP_MISMATCH\",\"message\":\"Commission"
                                                            + " ownership"
                                                            + " mismatch\",\"status\":403,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "COMMISSION_NOT_FOUND",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"COMMISSION_NOT_FOUND\",\"message\":\"Commission"
                                                            + " not found\",\"status\":404,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Invalid state, stale card, or already processed card",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"INVALID_COMMISSION_STATUS_TRANSITION\",\"message\":\"Invalid"
                                                            + " commission status"
                                                            + " transition\",\"status\":409,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "INTERNAL_SERVER_ERROR",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Internal"
                                                            + " server"
                                                            + " error\",\"status\":500,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms20-example\",\"details\":[]}")))
    })
    public ResponseEntity<ApiResponse<ConfirmRevisionCardResponse>> confirm(
            Authentication authentication,
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody ConfirmRevisionCardRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Unauthorized");
        }
        long customerId;
        try {
            customerId = Long.parseLong(authentication.getName());
            if (customerId <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid customer ID in token");
        }
        return ResponseEntity.ok(
                ApiResponse.success(useCase.execute(id, customerId, request.messageId())));
    }
}
