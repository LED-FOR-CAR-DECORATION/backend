package com.lumintra.backend.device.infrastructure.integration;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.device.domain.service.FirmwareBinaryStorage;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.channels.Channels;

/**
 * Stores firmware binaries in the private Firebase/GCS bucket (falls back to the default bucket
 * when no private bucket is configured, the same rule the media module uses).
 */
@Service
public class GcsFirmwareBinaryStorage implements FirmwareBinaryStorage {

    private static final Logger log = LoggerFactory.getLogger(GcsFirmwareBinaryStorage.class);
    static final String CONTENT_TYPE = "application/octet-stream";
    static final String CACHE_CONTROL = "private, no-store";

    private final Storage storage;
    private final String bucket;

    public GcsFirmwareBinaryStorage(
            Storage storage,
            @Value("${app.firebase.private-storage-bucket:}") String privateBucket,
            @Value("${app.firebase.storage-bucket:lumintra-storage-default}") String defaultBucket) {
        this.storage = storage;
        this.bucket = privateBucket != null && !privateBucket.isBlank() ? privateBucket.trim() : defaultBucket;
    }

    @Override
    public void upload(String objectKey, byte[] content) {
        try {
            storage.create(BlobInfo.newBuilder(bucket, objectKey)
                    .setContentType(CONTENT_TYPE)
                    .setCacheControl(CACHE_CONTROL)
                    .build(), content);
        } catch (Exception ex) {
            log.error("Failed to upload firmware binary [bucket={}] [objectKey={}]", bucket, objectKey, ex);
            throw new StorageServiceException("Failed to upload firmware binary: " + ex.getMessage(), ex);
        }
    }

    @Override
    public StoredBinary open(String objectKey) {
        Blob blob;
        try {
            blob = storage.get(BlobId.of(bucket, objectKey));
        } catch (Exception ex) {
            log.error("Failed to look up firmware binary [bucket={}] [objectKey={}]", bucket, objectKey, ex);
            throw new StorageServiceException("Failed to read firmware binary: " + ex.getMessage(), ex);
        }
        if (blob == null || !blob.exists()) {
            log.error("Firmware binary missing from storage [bucket={}] [objectKey={}]", bucket, objectKey);
            throw new StorageServiceException("Firmware binary is missing from storage.");
        }
        // The reader fetches in chunks, so the binary is never held in memory as a whole.
        return new StoredBinary(blob.getSize(), Channels.newInputStream(blob.reader()));
    }

    @Override
    public void delete(String objectKey) {
        try {
            storage.delete(BlobId.of(bucket, objectKey));
        } catch (Exception ex) {
            log.warn("Failed to delete orphaned firmware binary [bucket={}] [objectKey={}]: {}",
                    bucket, objectKey, ex.getMessage());
        }
    }
}
