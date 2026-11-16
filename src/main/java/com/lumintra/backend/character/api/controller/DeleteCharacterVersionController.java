package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.application.DeleteCharacterVersionUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
public class DeleteCharacterVersionController {

    private final DeleteCharacterVersionUseCase deleteCharacterVersionUseCase;

    @Operation(
            summary = "Delete character version",
            description = "Deletes an unlisted draft animation version for a graphic character. Enforces BR-FL5-07 (Scoped Designer Workspace) and BR-FL5-09 (Product Integrity Guard): only the authoring designer can delete the version, and deletion is rejected with 409 Conflict if the version has already been listed on the Store. Returns 204 No Content upon success."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "204",
                    description = "Character version deleted successfully (empty body)"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: characterId or versionId is non-positive",
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
                                                  "field": "versionId",
                                                  "message": "versionId must be greater than 0"
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing, expired, or invalid JWT authentication token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller does not possess DESIGNER role, employee account is inactive, or designer does not own the character",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found: character or character version with specified ID does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Version Not Found",
                                    value = """
                                            {
                                              "code": "VERSION_NOT_FOUND",
                                              "message": "Character version not found with id: 99",
                                              "status": 404,
                                              "timestamp": "2026-11-15T08:30:00Z",
                                              "traceId": "trace-12345"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflict: version has already been listed on the Store",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Version Already Linked To Product",
                                    value = """
                                            {
                                              "code": "VERSION_ALREADY_LINKED_TO_PRODUCT",
                                              "message": "Cannot delete character version with id 25 because it is listed on the Store (productId: 108).",
                                              "status": 409,
                                              "timestamp": "2026-11-15T08:30:00Z",
                                              "traceId": "trace-12345"
                                            }
                                            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @DeleteMapping("/api/management/characters/{characterId}/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<Void> deleteVersion(
            Authentication authentication,
            @Parameter(description = "ID of the target character", example = "12")
            @PathVariable("characterId") @Positive(message = "characterId must be greater than 0") Long characterId,
            @Parameter(description = "ID of the character version to delete", example = "25")
            @PathVariable("versionId") @Positive(message = "versionId must be greater than 0") Long versionId
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }

        deleteCharacterVersionUseCase.execute(authentication.getName(), characterId, versionId);
        return ResponseEntity.noContent().build();
    }
}
