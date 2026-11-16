package com.lumintra.backend.media.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Response payload containing pre-signed upload URL and public CDN asset URL")
public record SignedUploadUrlResponse(

        @Schema(
                description = "Time-limited V4 pre-signed PUT URL to upload binary directly to Firebase Cloud Storage",
                example = "https://storage.googleapis.com/lumintra-assets/characters/4/123e4567-anim.gif?X-Goog-Algorithm=..."
        )
        String signedUploadUrl,

        @Schema(
                description = "Permanent public/CDN URL to reference the uploaded file in downstream endpoints",
                example = "https://cdn.lumintra.com/characters/4/123e4567-anim.gif"
        )
        String publicUrl,

        @Schema(
                description = "Bucket object key path under which the asset will reside",
                example = "characters/4/123e4567-anim.gif"
        )
        String objectKey,

        @Schema(
                description = "Expiration timestamp of the signed upload URL in ISO-8601 format",
                example = "2026-11-15T23:25:00Z"
        )
        Instant expiresAt
) {
}
