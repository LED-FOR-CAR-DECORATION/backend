package com.lumintra.backend.media.api.controller;

import com.lumintra.backend.media.api.request.CreateSignedUploadUrlRequest;
import com.lumintra.backend.media.api.response.SignedUploadUrlResponse;
import com.lumintra.backend.media.application.CreateSignedUploadUrlUseCase;
import com.lumintra.backend.shared.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Media Service", description = "Endpoints for requesting short-lived direct-to-cloud signed upload URLs")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Validated
public class MediaUploadController {

    private final CreateSignedUploadUrlUseCase createSignedUploadUrlUseCase;

    @Operation(
            summary = "Request a short-lived V4 signed upload URL for direct cloud storage upload",
            description = "Issues a signed PUT upload URL locking MIME type, size limit (10MB), and public CDN cache headers. Returns the signed URL, permanent CDN public URL, and expiration timestamp.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/upload-url")
    public ResponseEntity<ApiResponse<SignedUploadUrlResponse>> createSignedUploadUrl(
            @Valid @RequestBody CreateSignedUploadUrlRequest request,
            Authentication authentication) {
        String subject = authentication != null ? authentication.getName() : null;
        Set<String> roles = authentication != null
                ? authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(auth -> auth.startsWith("ROLE_") ? auth.substring(5) : auth)
                .collect(Collectors.toSet())
                : Set.of();

        SignedUploadUrlResponse response = createSignedUploadUrlUseCase.execute(subject, roles, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
