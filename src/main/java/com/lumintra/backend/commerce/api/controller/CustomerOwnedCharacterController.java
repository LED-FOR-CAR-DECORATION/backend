package com.lumintra.backend.commerce.api.controller;

import com.lumintra.backend.commerce.api.response.CustomerOwnedCharacterResponse;
import com.lumintra.backend.commerce.application.ListCustomerOwnedCharactersUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.exception.iam.InvalidCredentialsException;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Customer Character Gallery",
        description = "Endpoints for customers to view purchased characters and animations from Store orders"
)
@RestController
@Validated
@RequiredArgsConstructor
public class CustomerOwnedCharacterController {

    private final ListCustomerOwnedCharactersUseCase listCustomerOwnedCharactersUseCase;

    @Operation(
            summary = "List customer owned characters",
            description = "Retrieves all character animations purchased and owned by the authenticated customer. Strictly ordered by purchase timestamp descending. Returns order and store product linkage for purchases, along with animation specifications (previewGifUrl, frameCount, frameDelayMs) for connected LED display rendering."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Owned characters retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CustomerOwnedCharacterResponse.class))
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing or invalid authentication token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Unauthorized",
                                    value = """
                                            {
                                              "code": "UNAUTHORIZED",
                                              "message": "Authentication is required.",
                                              "status": 401,
                                              "timestamp": "2026-11-16T13:40:00Z",
                                              "traceId": "trace-123"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller lacks required CUSTOMER role",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Forbidden",
                                    value = """
                                            {
                                              "code": "FORBIDDEN",
                                              "message": "Access is denied.",
                                              "status": 403,
                                              "timestamp": "2026-11-16T13:40:00Z",
                                              "traceId": "trace-123"
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/api/customer/characters/owned")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<List<CustomerOwnedCharacterResponse>>> listOwnedCharacters(
            Authentication authentication
    ) {
        Long customerId = extractCustomerId(authentication);
        List<CustomerOwnedCharacterResponse> ownedCharacters = listCustomerOwnedCharactersUseCase.execute(customerId);
        return ResponseEntity.ok(ApiResponse.success(ownedCharacters));
    }

    private Long extractCustomerId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            throw new InvalidCredentialsException("Invalid customer credentials.");
        }
    }
}
