package com.lumintra.backend.device.api.controller;

import com.lumintra.backend.device.api.request.DeprecateFirmwareRequest;
import com.lumintra.backend.device.api.request.FirmwareFilterRequest;
import com.lumintra.backend.device.api.request.UploadFirmwareRequest;
import com.lumintra.backend.device.api.response.DeprecateFirmwareResponse;
import com.lumintra.backend.device.api.response.FirmwareReleaseResponse;
import com.lumintra.backend.device.api.response.UploadFirmwareResponse;
import com.lumintra.backend.device.application.DeprecateFirmwareUseCase;
import com.lumintra.backend.device.application.ListFirmwareReleasesUseCase;
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
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/management/admin/firmware")
@RequiredArgsConstructor
@Tag(name = "Firmware Management", description = "ADMIN firmware release catalog for LED models (Flow 4)")
public class ManagedFirmwareController {

    private final UploadFirmwareUseCase uploadFirmwareUseCase;
    private final ListFirmwareReleasesUseCase listFirmwareReleasesUseCase;
    private final DeprecateFirmwareUseCase deprecateFirmwareUseCase;

    @Operation(
            summary = "Deprecate a firmware release (DEV-13)",
            description = "ADMIN only (BR-FL4-04). Marks the release DEPRECATED with the reason, time and admin; the row and "
                    + "stored binary are kept (BR-FL4-05) and deprecation cannot be undone. If the release was ACTIVE, the "
                    + "model's highest-version SUPERSEDED release becomes ACTIVE (rollback); with none, the model has no "
                    + "ACTIVE release. Deprecating a SUPERSEDED release promotes nothing.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Release deprecated; returns deprecated, newActiveFirmware and affectedDeviceCount"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED: reason missing, blank or longer than 1000 characters",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not an active ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "FIRMWARE_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "FIRMWARE_ALREADY_DEPRECATED",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/deprecate")
    public ResponseEntity<ApiResponse<DeprecateFirmwareResponse>> deprecateFirmware(
            Authentication authentication,
            @PathVariable("id") @Positive(message = "id must be a positive integer") Long id,
            @Valid @RequestBody DeprecateFirmwareRequest request) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return ResponseEntity.ok(ApiResponse.success(
                deprecateFirmwareUseCase.execute(authentication.getName(), id, request)));
    }

    @Operation(
            summary = "List firmware releases (DEV-11)",
            description = "ADMIN only (BR-FL4-04). Paginated catalog (size up to 100) filtered by modelId and status "
                    + "(ACTIVE, SUPERSEDED, DEPRECATED), newest upload first. Items are in data, paging in meta. "
                    + "installedDeviceCount is the number of customer-bound (BOUND) devices recorded on the release. "
                    + "status=ACTIVE&size=1 returns the latest ACTIVE release.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Firmware releases retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "VALIDATION_FAILED: invalid page, size, modelId or status",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not ADMIN",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<FirmwareReleaseResponse>>> listFirmwareReleases(
            @Valid @ModelAttribute FirmwareFilterRequest filter) {
        ListFirmwareReleasesUseCase.Result result = listFirmwareReleasesUseCase.execute(filter);
        return ResponseEntity.ok(ApiResponse.success(result.items(), result.meta()));
    }

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
