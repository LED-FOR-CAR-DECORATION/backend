package com.lumintra.backend.device.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.device.api.response.OtaManifestResponse;
import com.lumintra.backend.device.application.FetchOtaManifestUseCase;
import com.lumintra.backend.device.application.StartFirmwareDownloadUseCase;
import com.lumintra.backend.shared.exception.ErrorResponse;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device-facing OTA download endpoints (Flow 4). No login: the one-time token issued by DEV-21 is the only
 * credential, so these paths are public in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/api/firmware/downloads")
@RequiredArgsConstructor
@Tag(name = "Firmware Downloads", description = "Device-facing OTA manifest and binary download, authorised by a one-time token")
public class FirmwareDownloadController {

    /** The firmware rejects manifests of 2 KB or more. */
    static final int MAX_MANIFEST_BYTES = 2048;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final FetchOtaManifestUseCase fetchOtaManifestUseCase;
    private final StartFirmwareDownloadUseCase startFirmwareDownloadUseCase;

    @Operation(
            summary = "Fetch the OTA manifest (DEV-22)",
            description = "Called by the LED device, not the app. The path token is the one-time token from DEV-21 and the "
                    + "only credential. Returns the device's base MAC, the session nonce and the target firmware's version, "
                    + "SHA-256 and size so the device can confirm the update is meant for it. Always sent with "
                    + "Content-Length and under 2 KB. Repeat calls within the 10-minute window are allowed. An unknown, "
                    + "expired or revoked token, or a device that is no longer BOUND, all return the same 404."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Manifest",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = OtaManifestResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OTA_SESSION_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{token}/manifest")
    public ResponseEntity<byte[]> getManifest(
            @Parameter(description = "One-time 64-hex OTA token", required = true) @PathVariable String token)
            throws JsonProcessingException {
        OtaManifestResponse manifest = fetchOtaManifestUseCase.execute(token);
        // Serialised here (not by the message converter) so the exact Content-Length can be sent; the firmware needs it.
        byte[] body = JSON.writeValueAsBytes(ApiResponse.success(manifest));
        if (body.length >= MAX_MANIFEST_BYTES) {
            throw new IllegalStateException("OTA manifest is " + body.length + " bytes; the device accepts under "
                    + MAX_MANIFEST_BYTES);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    @Operation(
            summary = "Download the firmware binary (DEV-23)",
            description = "Called by the LED device, not the app. Same token rules as the manifest. Streams the binary "
                    + "from private storage through the backend as application/octet-stream with Content-Length equal to "
                    + "the file size; never redirects. Up to 3 attempts per session within the 10-minute window; further "
                    + "attempts, and any unknown, expired, revoked or unbound token, return the same 404."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Firmware binary",
                    content = @Content(mediaType = "application/octet-stream")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "OTA_SESSION_NOT_FOUND",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500",
                    description = "STORAGE_SERVICE_ERROR: the stored binary is missing or does not match the release",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{token}/binary")
    public ResponseEntity<Resource> getBinary(
            @Parameter(description = "One-time 64-hex OTA token", required = true) @PathVariable String token) {
        StartFirmwareDownloadUseCase.FirmwareDownload download = startFirmwareDownloadUseCase.execute(token);
        // InputStreamResource is copied to the response in chunks; Content-Length is set from the known size so the
        // converter never has to read the stream to measure it.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(download.size())
                .cacheControl(CacheControl.noStore())
                .body(new InputStreamResource(download.content()));
    }
}
