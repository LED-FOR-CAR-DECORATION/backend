package com.lumintra.backend.device.domain.service;

import java.io.InputStream;

/**
 * Private storage for firmware binaries. Implementations must not expose public URLs.
 */
public interface FirmwareBinaryStorage {

    /** Stores (or overwrites) the binary under {@code objectKey}. */
    void upload(String objectKey, byte[] content);

    /** Best-effort removal; must not throw when the object is already gone. */
    void delete(String objectKey);

    /**
     * Opens the stored binary for streaming without loading it into memory. Fails (before any byte is read)
     * when the object does not exist; the caller closes the stream.
     */
    StoredBinary open(String objectKey);

    /** An opened binary: its stored size in bytes and a stream over its content. */
    record StoredBinary(long size, InputStream content) {
    }

    static String objectKey(Long modelId, String version) {
        return "firmware/" + modelId + "/" + version + ".bin";
    }
}
