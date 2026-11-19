package com.lumintra.backend.media.infrastructure.integration;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.media.domain.service.MediaKeyGenerator;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GoogleCloudStorageSignedUrlService implements StorageSignedUrlService {

    private static final Logger log = LoggerFactory.getLogger(GoogleCloudStorageSignedUrlService.class);

    static final String PUBLIC_CACHE_CONTROL = "public, max-age=31536000, immutable";
    static final String PRIVATE_CACHE_CONTROL = "private, max-age=600";
    private static final String DEFAULT_MAX_LENGTH_RANGE = "0,10485760";

    private final Storage storage;

    @Value("${app.firebase.storage-bucket:lumintra-storage-default}")
    private String bucketName;

    @Value("${app.firebase.private-storage-bucket:}")
    private String privateBucketName;

    @Value("${app.cdn.base-url:https://cdn.lumintra.com}")
    private String cdnBaseUrl;

    @Override
    public SignedUploadUrlResult generateV4SignedPutUrl(
            String objectKey,
            String contentType,
            long maxFileSize,
            Duration expiration) {
        boolean privateObject = MediaKeyGenerator.isPrivateKey(objectKey);
        String targetBucket = privateObject ? resolvePrivateBucket() : bucketName;
        String cacheControl = privateObject ? PRIVATE_CACHE_CONTROL : PUBLIC_CACHE_CONTROL;
        try {
            BlobInfo blobInfo = BlobInfo.newBuilder(targetBucket, objectKey)
                    .setContentType(contentType)
                    .setCacheControl(cacheControl)
                    .build();

            Map<String, String> extensionHeaders = new LinkedHashMap<>();
            extensionHeaders.put("Content-Type", contentType);
            extensionHeaders.put("Cache-Control", cacheControl);
            extensionHeaders.put("x-goog-content-length-range", DEFAULT_MAX_LENGTH_RANGE);

            URL signedUrl = storage.signUrl(
                    blobInfo,
                    toMinutes(expiration),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                    Storage.SignUrlOption.withExtHeaders(extensionHeaders),
                    Storage.SignUrlOption.withV4Signature()
            );

            String publicUrl = privateObject ? null : publicUrlFor(objectKey);

            return new SignedUploadUrlResult(
                    signedUrl.toString(),
                    publicUrl,
                    objectKey,
                    Collections.unmodifiableMap(extensionHeaders)
            );
        } catch (Exception ex) {
            log.error("Failed to generate V4 signed PUT upload URL [bucket={}] [objectKey={}]", targetBucket, objectKey, ex);
            throw new StorageServiceException("Failed to generate signed upload URL: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String generateV4SignedGetUrl(String objectKey, Duration expiration) {
        String privateBucket = resolvePrivateBucket();
        try {
            URL signedUrl = storage.signUrl(
                    BlobInfo.newBuilder(privateBucket, objectKey).build(),
                    toMinutes(expiration),
                    TimeUnit.MINUTES,
                    Storage.SignUrlOption.httpMethod(HttpMethod.GET),
                    Storage.SignUrlOption.withV4Signature()
            );
            return signedUrl.toString();
        } catch (Exception ex) {
            log.error("Failed to generate V4 signed GET URL [bucket={}] [objectKey={}]", privateBucket, objectKey, ex);
            throw new StorageServiceException("Failed to generate signed read URL: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean privateObjectExists(String objectKey) {
        String privateBucket = resolvePrivateBucket();
        try {
            return storage.get(
                    BlobId.of(privateBucket, objectKey),
                    Storage.BlobGetOption.fields(Storage.BlobField.NAME)
            ) != null;
        } catch (Exception ex) {
            log.error("Failed to look up private object [bucket={}] [objectKey={}]", privateBucket, objectKey, ex);
            throw new StorageServiceException("Failed to verify uploaded media: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String publicUrlFor(String objectKey) {
        if (cdnBaseUrl != null && !cdnBaseUrl.isBlank()) {
            String cleanBase = cdnBaseUrl.endsWith("/") ? cdnBaseUrl.substring(0, cdnBaseUrl.length() - 1) : cdnBaseUrl;
            return cleanBase + "/" + objectKey;
        }
        return "https://storage.googleapis.com/" + bucketName + "/" + objectKey;
    }

    private String resolvePrivateBucket() {
        return privateBucketName != null && !privateBucketName.isBlank() ? privateBucketName.trim() : bucketName;
    }

    private static long toMinutes(Duration expiration) {
        return Math.max(1, expiration != null ? expiration.toMinutes() : 15);
    }
}
