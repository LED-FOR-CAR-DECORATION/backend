package com.lumintra.backend.media.domain.service;

import com.lumintra.backend.media.domain.model.MediaPurpose;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sanitizes input filenames against path traversal attacks and constructs scoped object keys per MED-01 AC03.
 */
@Component
public class MediaKeyGenerator {

    /**
     * Top-level folders whose objects live in the private bucket. Must stay in sync with
     * {@link MediaPurpose#isPrivate()} and {@link #folderFor(MediaPurpose, String, Long)}.
     */
    private static final List<String> PRIVATE_ROOT_FOLDERS = List.of("commissions/", "warranty/");

    /**
     * Shape of the file-name segment produced by {@link #generateObjectKey}: a random UUID, a dash, then the sanitized name.
     */
    private static final Pattern GENERATED_FILE_SEGMENT = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[A-Za-z0-9._-]+$"
    );

    public String generateObjectKey(MediaPurpose purpose, String userId, Long targetId, String rawFileName) {
        String cleanFileName = sanitizeFileName(rawFileName);
        String uuidPrefix = UUID.randomUUID().toString();
        return folderFor(purpose, userId, targetId) + uuidPrefix + "-" + cleanFileName;
    }

    /**
     * Folder (always ending in "/") that objects of the given purpose and scope are stored under.
     */
    public String folderFor(MediaPurpose purpose, String userId, Long targetId) {
        return switch (purpose) {
            case COMMISSION_REFERENCE -> "commissions/references/" + userId + "/";
            case COMMISSION_CHAT -> "commissions/" + targetId + "/chat/";
            case COMMISSION_DRAFT -> "commissions/" + targetId + "/drafts/";
            case WARRANTY_EVIDENCE -> "warranty/" + targetId + "/";
            case CHARACTER_ASSET -> "characters/" + userId + "/";
            case STORE_PRODUCT -> "store/products/";
            case PRODUCT_LED -> "store/products/led/";
            case PRODUCT_BUNDLE -> "store/products/bundle/";
            case PRODUCT_SUBSCRIPTION -> "store/products/subscription/";
        };
    }

    /**
     * True when the key is exactly one generated file directly inside {@code folder}.
     */
    public boolean isGeneratedKeyInFolder(String objectKey, String folder) {
        if (objectKey == null || folder == null || !objectKey.startsWith(folder)) {
            return false;
        }
        return GENERATED_FILE_SEGMENT.matcher(objectKey.substring(folder.length())).matches();
    }

    public static boolean isPrivateKey(String objectKey) {
        return objectKey != null && PRIVATE_ROOT_FOLDERS.stream().anyMatch(objectKey::startsWith);
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
