package com.lumintra.backend.character.api.controller;

import com.lumintra.backend.character.api.request.AdminCharacterFilterRequest;
import com.lumintra.backend.character.api.response.AdminCharacterItem;
import com.lumintra.backend.character.application.ListAllCharactersUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import com.lumintra.backend.shared.response.PaginationMeta;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "Admin Character Catalog",
        description = "Endpoints for management (MANAGER, ADMIN) to oversee all company graphic characters"
)
@RestController
@RequestMapping("/api/management/admin/characters")
@RequiredArgsConstructor
public class AdminCharacterController {

    private final ListAllCharactersUseCase listAllCharactersUseCase;

    @Operation(
            summary = "Admin list all characters",
            description = "Retrieves a paginated list of all graphic characters across the entire company for oversight, copyright audits, and project handovers. Supports optional keyword query filtering, optional creator employee filter, batch loading of author details from IAM, and version counts without N+1 queries."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Company character catalog retrieved successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Bad Request: invalid pagination or creator ID parameters",
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
                                                  "field": "page",
                                                  "message": "page must be greater than or equal to 0"
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
                    description = "Forbidden: caller does not possess the MANAGER or ADMIN role",
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
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminCharacterItem>>> listAllCharacters(
            @Valid @ModelAttribute AdminCharacterFilterRequest request) {
        Page<AdminCharacterItem> page = listAllCharactersUseCase.execute(request);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PaginationMeta.from(page)));
    }
}
