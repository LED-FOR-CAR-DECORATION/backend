package com.lumintra.backend.media.domain.service;

import java.time.Duration;
import java.util.Map;

/**
 * Domain service contract for cloud storage signing.
 * <p>
 * Objects under private folders (see {@link MediaKeyGenerator#isPrivateKey(String)}) live in the private bucket,
 * which has no public read access; everything else lives in the public bucket served through the CDN.
 */
public interface StorageSignedUrlService {

    /**
     * Issues a V4 signed PUT URL. The target bucket and Cache-Control header are chosen from the object key.
     */
    SignedUploadUrlResult generateV4SignedPutUrl(
            String objectKey,
            String contentType,
            long maxFileSize,
            Duration expiration
    );

    /**
     * Issues a short-lived V4 signed GET URL for an object in the private bucket.
     */
    String generateV4SignedGetUrl(String objectKey, Duration expiration);

    /**
     * Returns whether the object has actually been uploaded to the private bucket.
     */
    boolean privateObjectExists(String objectKey);

    /**
     * Permanent CDN (or public bucket) URL for an object in the public bucket.
     */
    String publicUrlFor(String objectKey);

    /**
     * @param publicUrl     permanent URL for public objects; {@code null} for private objects
     * @param uploadHeaders headers the client must send verbatim with the PUT request (they are part of the signature)
     */
    record SignedUploadUrlResult(
            String signedUploadUrl,
            String publicUrl,
            String objectKey,
            Map<String, String> uploadHeaders
    ) {
        public SignedUploadUrlResult(String signedUploadUrl, String publicUrl, String objectKey) {
            this(signedUploadUrl, publicUrl, objectKey, Map.of());
        }
    }
}
