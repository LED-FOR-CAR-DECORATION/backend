package com.lumintra.backend.media.domain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Optional;

/**
 * Extracts the bucket object key from a stored or submitted media reference.
 * <p>
 * A reference is either a bare object key (e.g. {@code commissions/105/chat/<uuid>-photo.png}, the format stored for
 * new private media) or a URL on one of our own media hosts (legacy rows and public media): the CDN, the public bucket
 * or the private bucket on {@code storage.googleapis.com}. Query strings (such as signatures) are ignored.
 */
@Component
public class MediaReferenceParser {

    private static final String GCS_HOST = "storage.googleapis.com";

    private final String cdnHost;
    private final String publicBucket;
    private final String privateBucket;

    public MediaReferenceParser(
            @Value("${app.cdn.base-url:https://cdn.lumintra.com}") String cdnBaseUrl,
            @Value("${app.firebase.storage-bucket:}") String publicBucket,
            @Value("${app.firebase.private-storage-bucket:}") String privateBucket) {
        this.cdnHost = hostOf(cdnBaseUrl);
        this.publicBucket = trimToEmpty(publicBucket);
        this.privateBucket = trimToEmpty(privateBucket);
    }

    public Optional<String> extractObjectKey(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        String trimmed = reference.trim();
        if (trimmed.contains("://")) {
            return extractFromUrl(trimmed);
        }
        return isSafeKey(trimmed) ? Optional.of(trimmed) : Optional.empty();
    }

    private Optional<String> extractFromUrl(String url) {
        if (!url.startsWith("https://")) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null || path.length() <= 1) {
            return Optional.empty();
        }

        String key = null;
        if (!cdnHost.isEmpty() && host.equalsIgnoreCase(cdnHost)) {
            key = path.substring(1);
        } else if (host.equalsIgnoreCase(GCS_HOST)) {
            key = stripBucketPrefix(path.substring(1));
        } else if (isBucketSubdomain(host, publicBucket) || isBucketSubdomain(host, privateBucket)) {
            key = path.substring(1);
        }
        return key != null && isSafeKey(key) ? Optional.of(key) : Optional.empty();
    }

    private String stripBucketPrefix(String pathWithoutSlash) {
        for (String bucket : new String[]{publicBucket, privateBucket}) {
            if (!bucket.isEmpty() && pathWithoutSlash.startsWith(bucket + "/")) {
                return pathWithoutSlash.substring(bucket.length() + 1);
            }
        }
        return null;
    }

    private static boolean isBucketSubdomain(String host, String bucket) {
        return !bucket.isEmpty() && host.equalsIgnoreCase(bucket + "." + GCS_HOST);
    }

    private static boolean isSafeKey(String key) {
        if (key.isEmpty() || key.startsWith("/") || key.contains("\\") || key.contains("?") || key.contains("#")) {
            return false;
        }
        for (String segment : key.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private static String hostOf(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(baseUrl.trim()).getHost();
            return host != null ? host : "";
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }
}
