package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.api.request.CreateCharacterVersionRequest;
import com.lumintra.backend.character.api.response.CreateCharacterVersionResponse;
import com.lumintra.backend.character.application.CreateCharacterVersionUseCase;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Character Catalog Management",
        description = "Endpoints for graphic designers to author and manage digital graphic characters"
)
@RestController
@Validated
@RequiredArgsConstructor
public class CreateCharacterVersionController {

    private final CreateCharacterVersionUseCase createCharacterVersionUseCase;

    @Operation(
            summary = "Create character version",
            description = "Uploads and registers a new animation revision for an existing graphic character. Enforces BR-FL5-07 / BR-FL5-10: only the authoring designer can create versions for their own characters. Automatically increments versionNumber, defaults product_id to null (unlisted), and updates any previous active versions to isCurrent = false if the new version is marked current. Returns 201 Created."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Character version created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateCharacterVersionResponse.class)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: validation failed for character ID or version details",
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
                                                  "field": "frameCount",
                                                  "reason": "Frame count must be greater than 0."
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
                    description = "Forbidden: non-designer role or designer is not the creator of this character",
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
                                              "message": "Character not found with id: 999",
                                              "status": 404,
                                              "timestamp": "2026-11-15T02:00:00Z",
                                              "traceId": "abc-123"
                                            }
                                            """
                            )
                    )
            )
    })
    @PostMapping("/api/management/characters/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DESIGNER')")
    public ResponseEntity<ApiResponse<CreateCharacterVersionResponse>> createVersion(
            @Parameter(description = "Unique identifier of the target character", required = true, example = "12")
            @PathVariable("id") @Positive(message = "Character ID must be a positive integer.") Long id,
            @Valid @RequestBody CreateCharacterVersionRequest request,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }

        CreateCharacterVersionResponse response = createCharacterVersionUseCase.execute(
                authentication.getName(),
                id,
                request
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
