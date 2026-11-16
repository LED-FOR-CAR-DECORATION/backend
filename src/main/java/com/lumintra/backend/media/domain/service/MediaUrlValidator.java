package com.lumintra.backend.media.domain.service;

import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Shared domain validator exposed according to MED-01 AC05.
 * Downstream endpoints saving media URLs verify that submitted URLs belong to the configured CDN or Firebase Storage bucket domain.
 */
@Component
public class MediaUrlValidator {

    private final String cdnBaseUrl;
    private final String storageBucket;

    public MediaUrlValidator(
            @Value("${lumintra.cdn.base-url:https://cdn.lumintra.com}") String cdnBaseUrl,
            @Value("${lumintra.storage.firebase.bucket:}") String storageBucket) {
        this.cdnBaseUrl = cdnBaseUrl != null ? cdnBaseUrl.trim() : "";
        this.storageBucket = storageBucket != null ? storageBucket.trim() : "";
    }

    public boolean isValidMediaUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("https://")) {
            return false;
        }
        try {
            URI uri = URI.create(trimmed);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return false;
            }

            // Check CDN Base URL
            if (!cdnBaseUrl.isBlank()) {
                URI cdnUri = URI.create(cdnBaseUrl);
                if (host.equalsIgnoreCase(cdnUri.getHost())) {
                    return true;
                }
            }

            // Check Google Cloud Storage domain: storage.googleapis.com/<bucket>/...
            if (host.equalsIgnoreCase("storage.googleapis.com")) {
                if (!storageBucket.isBlank()) {
                    String path = uri.getPath();
                    return path != null && (path.startsWith("/" + storageBucket + "/") || path.equals("/" + storageBucket));
                }
                return true;
            }

            // Check bucket-prefixed domain: <bucket>.storage.googleapis.com/...
            if (!storageBucket.isBlank() && host.equalsIgnoreCase(storageBucket + ".storage.googleapis.com")) {
                return true;
            }

            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    public void validateMediaUrl(String url) {
        if (!isValidMediaUrl(url)) {
            throw new InvalidMediaUrlException("Submitted media URL is invalid or does not belong to authorized CDN / storage domains.");
        }
    }
}
