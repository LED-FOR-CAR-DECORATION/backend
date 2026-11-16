package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.application.DeleteCharacterUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Character Catalog Management",
        description = "Endpoints for graphic designers to author and manage digital graphic characters"
)
@RestController
@Validated
@RequiredArgsConstructor
public class DeleteCharacterController {

    private final DeleteCharacterUseCase deleteCharacterUseCase;

    @Operation(
            summary = "Delete graphic character",
            description = "Deletes an unlisted draft graphic character and cascades deletion to all unlisted versions. Enforces BR-FL5-07 (Scoped Designer Workspace) and BR-FL5-09 (Product Integrity Guard): only the authoring designer can delete the character, and deletion is rejected with 409 Conflict if any version has already been listed on the Store. Returns 204 No Content upon success."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204",
                    description = "Graphic character and unlisted versions deleted successfully (empty body)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: character ID is non-positive",
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
                                              "timestamp": "2026-11-15T08:30:00Z",
                                              "traceId": "trace-12345",
                                              "details": [
                                                {
                                                  "field": "id",
                                                  "message": "id must be greater than 0"
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
                    responseCode = "409",
                    description = "Conflict: character has one or more versions listed on the Store",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Cannot Delete Listed Character",
                                    value = """
                                            {
                                              "code": "CANNOT_DELETE_LISTED_CHARACTER",
                                              "message": "Cannot delete character with id 12 because one or more versions are listed on the Store.",
                                              "status": 409,
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
    @DeleteMapping("/api/management/characters/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<Void> deleteCharacter(
            Authentication authentication,
            @Parameter(description = "ID of the character to delete", example = "12")
            @PathVariable("id") @Positive(message = "id must be greater than 0") Long id
    ) {
        String subject = extractAuthenticatedSubject(authentication);
        deleteCharacterUseCase.execute(subject, id);
        return ResponseEntity.noContent().build();
    }

    private String extractAuthenticatedSubject(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return authentication.getName();
    }
}
