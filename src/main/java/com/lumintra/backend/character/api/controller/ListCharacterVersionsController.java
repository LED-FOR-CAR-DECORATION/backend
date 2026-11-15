package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.application.ListCharacterVersionsUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Character Catalog Management",
        description = "Endpoints for graphic designers and management to inspect and manage character versions"
)
@RestController
@Validated
@RequiredArgsConstructor
public class ListCharacterVersionsController {

    private final ListCharacterVersionsUseCase listCharacterVersionsUseCase;

    @Operation(
            summary = "List character versions with store status",
            description = "Retrieves all versions of a character ordered by version number descending. DESIGNER callers can only inspect versions of characters they created (BR-FL5-07 / BR-FL5-10). MANAGER and ADMIN callers can inspect versions for any character across the company. Includes Store listing metadata (isListedOnStore, productId, productStatus) and strictly omits proprietary LED binary URLs."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Character versions retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = CharacterVersionResponse.class))
                    )
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
                                              "timestamp": "2026-11-15T02:00:00Z",
                                              "traceId": "abc-123",
                                              "details": [
                                                {
                                                  "field": "characterId",
                                                  "reason": "Character ID must be a positive integer."
                                                }
                                              ]
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized: missing or invalid authentication token",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Forbidden: caller lacks required roles (DESIGNER, MANAGER, ADMIN) or designer is not the creator of this character",
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
                                              "timestamp": "2026-11-15T02:00:00Z",
                                              "traceId": "abc-123"
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Not Found: character ID does not exist",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(
                                    name = "Character Not Found",
                                    value = """
                                            {
                                              "code": "CHARACTER_NOT_FOUND",
                                              "message": "Graphic character not found with id: 999",
                                              "status": 404,
                                              "timestamp": "2026-11-15T02:00:00Z",
                                              "traceId": "abc-123"
                                            }
                                            """
                            )
                    )
            )
    })
    @GetMapping("/api/management/characters/{id}/versions")
    @PreAuthorize("hasAnyRole('DESIGNER', 'MANAGER', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<CharacterVersionResponse>>> listVersions(
            @Parameter(description = "Unique identifier of the character", required = true, example = "12")
            @PathVariable("id") @Positive(message = "Character ID must be a positive integer.") Long id,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }

        boolean isManagerOrAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> "ROLE_MANAGER".equals(auth) || "ROLE_ADMIN".equals(auth));

        List<CharacterVersionResponse> versions = listCharacterVersionsUseCase.execute(
                authentication.getName(),
                isManagerOrAdmin,
                id
        );

        return ResponseEntity.ok(ApiResponse.success(versions));
    }
}
