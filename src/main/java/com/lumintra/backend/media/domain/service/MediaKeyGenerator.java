package com.lumintra.backend.media.domain.service;

import com.lumintra.backend.media.domain.model.MediaPurpose;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.UUID;

/**
 * Sanitizes input filenames against path traversal attacks and constructs scoped object keys per MED-01 AC03.
 */
@Component
public class MediaKeyGenerator {

    public String generateObjectKey(MediaPurpose purpose, String userId, Long targetId, String rawFileName) {
        String cleanFileName = sanitizeFileName(rawFileName);
        String uuidPrefix = UUID.randomUUID().toString();
        String uniqueFileName = uuidPrefix + "-" + cleanFileName;

        return switch (purpose) {
            case COMMISSION_REFERENCE -> "commissions/references/" + userId + "/" + uniqueFileName;
            case COMMISSION_CHAT -> "commissions/" + targetId + "/chat/" + uniqueFileName;
            case COMMISSION_DRAFT -> "commissions/" + targetId + "/drafts/" + uniqueFileName;
            case WARRANTY_EVIDENCE -> "warranty/" + targetId + "/" + uniqueFileName;
            case CHARACTER_ASSET -> "characters/" + userId + "/" + uniqueFileName;
            case STORE_PRODUCT -> "store/products/" + uniqueFileName;
        };
    }

    public String sanitizeFileName(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return "asset.bin";
        }
        // Extract filename to neutralize path traversal
        String nameOnly = Paths.get(rawFileName).getFileName().toString();
        String sanitized = nameOnly.replaceAll("[^a-zA-Z0-9._-]", "-")
                .replaceAll("-+", "-");
        if (sanitized.isBlank() || sanitized.equals(".") || sanitized.equals("..")) {
            return "asset.bin";
        }
        return sanitized;
    }
}
