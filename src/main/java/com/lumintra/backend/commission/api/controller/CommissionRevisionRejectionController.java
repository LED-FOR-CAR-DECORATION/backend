package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.request.RejectRevisionCardRequest;
import com.lumintra.backend.commission.api.response.RejectRevisionCardResponse;
import com.lumintra.backend.commission.application.RejectRevisionCardUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
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
@Tag(name = "Commission Revision Cards", description = "Reject inaccurate customer revision briefs")
public class CommissionRevisionRejectionController {
    private final RejectRevisionCardUseCase useCase;

    @PostMapping("/api/commissions/{id}/revision-card/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(
            summary = "Reject pending revision brief card",
            description =
                    "The owning customer rejects the latest pending revision card when it does not"
                            + " match their request. Consumes 0 quota and 0 fees, and keeps the"
                            + " commission in PREVIEW_DELIVERED so staff can send a revised card.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Revision card rejected; commission remains PREVIEW_DELIVERED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Invalid commission ID, message ID, or reason",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Authentication required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Owning CUSTOMER required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "COMMISSION_NOT_FOUND",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Invalid state, stale card, or already processed card",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ErrorResponse.class))),
    })
    public ResponseEntity<ApiResponse<RejectRevisionCardResponse>> reject(
            Authentication authentication,
            @PathVariable("id") @Positive Long id,
            @Valid @RequestBody RejectRevisionCardRequest request) {
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
                ApiResponse.success(
                        useCase.execute(id, customerId, request.messageId(), request.reason())));
    }
}
