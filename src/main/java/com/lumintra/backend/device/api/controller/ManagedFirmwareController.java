package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.application.UploadFirmwareUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/admin/firmware")
@RequiredArgsConstructor
@Tag(name = "Firmware Management", description = "ADMIN firmware release catalog for LED models (Flow 4)")
public class ManagedFirmwareController {

    private final UploadFirmwareUseCase uploadFirmwareUseCase;

    @Operation(
            summary = "Upload a firmware release (DEV-12)",
            description = "ADMIN only (BR-FL4-04). Accepts an ESP32 .bin image with modelId, semantic version and changelog. "
                    + "The server checks the 0xE9 magic byte and size limit (BR-GEN-08), computes SHA-256 (BR-FL4-02), stores the "
                    + "binary privately at firmware/{modelId}/{version}.bin and makes it the model's single ACTIVE release; the "
                    + "previous ACTIVE release becomes SUPERSEDED. DEPRECATED LED models are allowed (BR-FL5-12).",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Firmware release created and ACTIVE"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "INVALID_FIRMWARE_BINARY (empty, too small, too large, missing 0xE9) or VALIDATION_FAILED (fields)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Invalid binary", value = """
                                    {
                                      "code": "INVALID_FIRMWARE_BINARY",
                                      "message": "Firmware file is not an ESP32 image (missing 0xE9 magic byte).",
                                      "status": 400,
                                      "timestamp": "2026-11-21T08:00:00Z",
                                      "traceId": "trace-12345",
                                      "details": []
                                    }
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an active ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "LED_MODEL_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "FIRMWARE_VERSION_CONFLICT: version exists or is not higher than every existing version of the model",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413",
                    description = "PAYLOAD_TOO_LARGE: request exceeds the server multipart limit",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UploadFirmwareResponse>> uploadFirmware(
            Authentication authentication,
            @Valid @ModelAttribute UploadFirmwareRequest request) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        UploadFirmwareResponse response = uploadFirmwareUseCase.execute(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}
