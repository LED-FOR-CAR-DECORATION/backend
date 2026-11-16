package com.lumintra.backend.media.infrastructure.integration;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GoogleCloudStorageSignedUrlService implements StorageSignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudStorageSignedUrlService.class);

    private static final String CACHE_CONTROL_HEADER = "public, max-age=31536000, immutable";
    private static final String DEFAULT_MAX_LENGTH_RANGE = "0,10485760";

    private final Storage storage;

    @Value("${lumintra.storage.firebase.bucket:lumintra-storage-default}")
    private String bucketName;

    @Value("${lumintra.cdn.base-url:https://cdn.lumintra.com}")
    private String cdnBaseUrl;

    @Override
    public SignedUploadUrlResult generateV4SignedPutUrl(
            String objectKey,
            String contentType,
            long maxFileSize,
            Duration expiration) {
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, objectKey)
                    .setContentType(contentType)
                    .setCacheControl(CACHE_CONTROL_HEADER)
                    .build();

            Map<String, String> extensionHeaders = new HashMap<>();
            extensionHeaders.put("Content-Type", contentType);
            extensionHeaders.put("Cache-Control", CACHE_CONTROL_HEADER);
            extensionHeaders.put("x-goog-content-length-range", DEFAULT_MAX_LENGTH_RANGE);

            long durationMinutes = Math.max(1, expiration != null ? expiration.toMinutes() : 15);

            URL signedUrl = storage.signUrl(
                    blobInfo,
                    durationMinutes,
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withExtHeaders(extensionHeaders),
                    Storage.SignUrlOption.withV4Signature()
            );

            String publicUrl = buildPublicUrl(objectKey);

            return new SignedUploadUrlResult(signedUrl.toString(), publicUrl, objectKey);
        } catch (Exception ex) {
            log.error("Failed to generate V4 signed PUT upload URL [bucket={}] [objectKey={}]", bucketName, objectKey, ex);
            throw new StorageServiceException("Failed to generate signed upload URL: " + ex.getMessage(), ex);
        }
    }

    private String buildPublicUrl(String objectKey) {
        if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
            String cleanBase = cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl;
            return cleanBase + "/" + objectKey;
        }
        return "https://storage.googleapis.com/" + bucketName + "/" + objectKey;
    }
}
