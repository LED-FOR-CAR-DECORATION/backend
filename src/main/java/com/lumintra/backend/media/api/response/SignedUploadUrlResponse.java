package com.lumintra.backend.media.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Response payload containing pre-signed upload URL and public CDN asset URL")
public record SignedUploadUrlResponse(

        @Schema(
                description = "Time-limited V4 pre-signed PUT URL to upload binary directly to Firebase Cloud Storage",
                example = "https://storage.googleapis.com/lumintra-assets/characters/4/123e4567-anim.gif?X-Goog-Algorithm=..."
        )
        String signedUploadUrl,

        @Schema(
                description = "Permanent public CDN URL. Only set for public purposes (CHARACTER_ASSET, STORE_PRODUCT); "
                        + "null for private purposes (COMMISSION_REFERENCE, COMMISSION_CHAT, COMMISSION_DRAFT, WARRANTY_EVIDENCE), "
                        + "which are only viewable through short-lived signed URLs returned by the owning endpoints.",
                example = "https://cdn.lumintra.com/characters/4/123e4567-anim.gif",
                nullable = true
        )
        String publicUrl,

        @Schema(
                description = "Bucket object key of the asset. For private purposes, send this value as the "
                        + "imageUrl / mediaUrl / evidenceMediaUrls item of the downstream endpoint.",
                example = "commissions/105/chat/123e4567-e89b-12d3-a456-426614174000-photo.png"
        )
        String objectKey,

        @Schema(
                description = "Headers the client must send verbatim with the PUT upload (they are part of the signature)",
                example = "{\"Content-Type\":\"image/png\",\"Cache-Control\":\"private, max-age=600\",\"x-goog-content-length-range\":\"0,10485760\"}"
        )
        Map<String, String> uploadHeaders,

        @Schema(
                description = "Expiration timestamp of the signed upload URL in ISO-8601 format",
                example = "2026-11-15T23:25:00Z"
        )
        Instant expiresAt
) {
}
