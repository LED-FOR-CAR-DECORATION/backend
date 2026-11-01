package com.lumintra.backend.firmware.api.controller;

import com.lumintra.backend.firmware.api.request.FirmwareRequests;
import com.lumintra.backend.firmware.api.response.FirmwareResponses.*;
import com.lumintra.backend.firmware.application.FirmwareUseCases;
import com.lumintra.backend.shared.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController @RequiredArgsConstructor
public class FirmwareController {
    private final FirmwareUseCases firmware;
    @GetMapping("/api/management/admin/firmware") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<Release>> list() { return ApiResponse.success(firmware.list()); }
    @PostMapping(value="/api/management/admin/firmware", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Release>> upload(@RequestParam MultipartFile file, @RequestParam String version, @RequestParam String hardwareRevision) throws IOException {
        return ResponseEntity.status(201).body(ApiResponse.success(firmware.upload(file.getBytes(), file.getOriginalFilename(), version, hardwareRevision)));
    }
    @PatchMapping("/api/management/admin/firmware/{id}/activate") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Release> activate(@PathVariable String id) { return ApiResponse.success(firmware.activate(id)); }
    @PatchMapping("/api/management/admin/firmware/{id}/deprecate") @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Release> deprecate(@PathVariable String id) { return ApiResponse.success(firmware.deprecate(id)); }
    @PostMapping("/api/management/admin/firmware/devices") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Device>> provision(@Valid @RequestBody FirmwareRequests.Device request) {
        return ResponseEntity.status(201).body(ApiResponse.success(firmware.provision(request)));
    }
    @GetMapping("/api/devices/{id}/ota/check")
    public ApiResponse<Check> check(@PathVariable String id, @RequestParam String currentVersion, Principal principal) {
        return ApiResponse.success(firmware.check(id, Long.valueOf(principal.getName()), currentVersion));
    }
    @PostMapping("/api/devices/{id}/ota/sessions")
    public ResponseEntity<ApiResponse<Session>> begin(@PathVariable String id, @Valid @RequestBody FirmwareRequests.Session request, Principal principal) {
        return ResponseEntity.status(201).body(ApiResponse.success(firmware.begin(id,Long.valueOf(principal.getName()),request)));
    }
    // Opaque, short-lived device capability. Never use the user's JWT on the ESP32.
    @GetMapping("/api/firmware/downloads/{token}/manifest")
    public ResponseEntity<ApiResponse<Manifest>> manifest(@PathVariable String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(firmware.manifest(token)));
    }
    // Deliberate binary transport exception, documented in docs/firmware-ota.md.
    @GetMapping(value="/api/firmware/downloads/{token}/binary", produces=MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> binary(@PathVariable String token) {
        byte[] image = firmware.binary(token).bytes();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(image.length).header("Content-Disposition", "attachment; filename=firmware.bin").body(image);
    }
}
