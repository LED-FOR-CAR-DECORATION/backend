package com.lumintra.backend.device.domain.service;

/**
 * Private storage for firmware binaries. Objects are never public: devices fetch them through the
 * backend (DEV-22/23), never through a direct storage link.
 */
public interface FirmwareBinaryStorage {

    /** Stores (or overwrites) the binary under {@code objectKey}. */
    void upload(String objectKey, byte[] content);

    /** Best-effort removal; must not throw when the object is already gone. */
    void delete(String objectKey);

    static String objectKey(Long modelId, String version) {
        return "firmware/" + modelId + "/" + version + ".bin";
    }
}
