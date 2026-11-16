package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.api.request.UpdateCharacterRequest;
import com.lumintra.backend.character.api.response.UpdateCharacterResponse;
import com.lumintra.backend.character.application.UpdateCharacterUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Character Catalog Management",
        description = "Endpoints for graphic designers to author and manage digital graphic characters"
)
@RestController
@Validated
@RequiredArgsConstructor
public class UpdateCharacterController {

    private final UpdateCharacterUseCase updateCharacterUseCase;

    @Operation(
            summary = "Update graphic character",
            description = "Updates the name and description of an existing graphic character. Enforces BR-FL5-07 (Scoped Designer Workspace): only the authenticated graphic designer who authored the character is permitted to update it. Rejects other designers, staff, managers, admins, or customers with 403 Forbidden. Returns 404 Not Found if the character does not exist."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Graphic character updated successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: characterName is blank, exceeds 255 characters, or id is non-positive",
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
                                              "timestamp": "2026-11-15T08:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "characterName",
                                                  "message": "characterName must not be blank"
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller does not possess DESIGNER role, employee account is inactive, or designer does not own the character",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: character with specified ID does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Character Not Found",
                                    value = """
                                            {
                                              "code": "CHARACTER_NOT_FOUND",
                                              "message": "Character not found with id: 999",
                                              "status": 404,
                                              "timestamp": "2026-11-15T08:30:00Z",
                                              "traceId": "trace-12345"
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
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PutMapping("/api/management/characters/{id}")
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<ApiResponse<UpdateCharacterResponse>> updateCharacter(
            Authentication authentication,
            @Parameter(description = "ID of the character to update", example = "12")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id,
            @Valid @RequestBody UpdateCharacterRequest request
    ) {
        String subject = extractAuthenticatedSubject(authentication);
        UpdateCharacterResponse response = updateCharacterUseCase.execute(subject, id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private String extractAuthenticatedSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return authentication.getName();
    }
}
