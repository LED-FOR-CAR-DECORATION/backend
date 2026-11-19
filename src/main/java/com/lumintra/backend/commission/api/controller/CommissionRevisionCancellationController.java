package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.application.CancelRevisionCheckoutUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Positive;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Commission Revision Cards", description = "Cancel pending paid revision checkouts")
public class CommissionRevisionCancellationController {
    private final CancelRevisionCheckoutUseCase useCase;

    @PostMapping("/api/commissions/{id}/revision-card/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Cancel pending extra-fee revision checkout",
            description =
                    "The owning customer cancels the active revision payment, removes its unpaid"
                            + " fee and returns to PREVIEW_DELIVERED. No request body. A completed"
                            + " payment cannot be cancelled.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "204",
                description = "Pending checkout cancelled; commission returned to preview",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid commission ID",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"VALIDATION_FAILED\",\"message\":\"Invalid"
                                                            + " commission"
                                                            + " ID\",\"status\":400,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}"))),
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
                                                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication"
                                                            + " required\",\"status\":401,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}"))),
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
                                                        "{\"code\":\"COMMISSION_OWNERSHIP_MISMATCH\",\"message\":\"Owning"
                                                            + " CUSTOMER"
                                                            + " required\",\"status\":403,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Commission or payment session not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"COMMISSION_NOT_FOUND\",\"message\":\"Commission"
                                                            + " or payment session not"
                                                            + " found\",\"status\":404,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Invalid checkout state, card, or payment already finalized",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"PAYMENT_SESSION_NOT_CANCELLABLE\",\"message\":\"Invalid"
                                                            + " checkout state, card, or payment"
                                                            + " already"
                                                            + " finalized\",\"status\":409,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "Unexpected cancellation failure",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class),
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"code\":\"INTERNAL_SERVER_ERROR\",\"message\":\"Unexpected"
                                                            + " cancellation"
                                                            + " failure\",\"status\":500,\"timestamp\":\"2026-11-19T03:00:00Z\",\"traceId\":\"cms22-example\",\"details\":[]}")))
    })
    public ResponseEntity<Void> cancel(
            Authentication authentication, @PathVariable("id") @Positive Long id) {
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
        useCase.execute(id, customerId);
        return ResponseEntity.noContent().build();
    }
}
