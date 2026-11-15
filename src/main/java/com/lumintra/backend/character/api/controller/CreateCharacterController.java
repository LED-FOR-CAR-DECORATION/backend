package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.api.request.CreateCharacterRequest;
import com.lumintra.backend.character.api.response.CreateCharacterResponse;
import com.lumintra.backend.character.application.CreateCharacterUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "Character Catalog Management",
        description = "Endpoints for graphic designers to author and manage digital graphic characters"
)
@RestController
@RequestMapping("/api/management/characters")
@RequiredArgsConstructor
public class CreateCharacterController {

    private final CreateCharacterUseCase createCharacterUseCase;

    @Operation(
            summary = "Create graphic character",
            description = "Creates a new graphic character catalog entry authored by the authenticated designer. The creator employee ID is automatically resolved from the caller's JWT and verified against the database. Rejects non-designers with 403 Forbidden."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Graphic character created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: characterName is blank or exceeds 255 characters",
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
                    description = "Forbidden: caller does not possess the DESIGNER role or employee account is inactive",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)
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
    @PreAuthorize("hasRole('DESIGNER')")
    @PostMapping
    public ResponseEntity<ApiResponse<CreateCharacterResponse>> createCharacter(
            Authentication authentication,
            @Valid @RequestBody CreateCharacterRequest request) {
        String subject = extractAuthenticatedSubject(authentication);
        CreateCharacterResponse response = createCharacterUseCase.execute(subject, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    private String extractAuthenticatedSubject(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return authentication.getName();
    }
}
