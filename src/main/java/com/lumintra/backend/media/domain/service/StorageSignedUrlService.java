package com.lumintra.backend.media.domain.service;

import java.time.Duration;

/**
 * Domain service contract for generating V4 signed PUT upload URLs on cloud storage.
 */
public interface StorageSignedUrlService {

    SignedUploadUrlResult generateV4SignedPutUrl(
            String objectKey,
            String contentType,
            long maxFileSize,
            Duration expiration
    );

    record SignedUploadUrlResult(
            String signedUploadUrl,
            String publicUrl,
            String objectKey
    ) {
    }
}
