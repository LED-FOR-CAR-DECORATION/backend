package com.lumintra.backend.media.domain.service;

import java.util.List;

/**
 * Turns a stored media reference into a URL a client can load.
 * <p>
 * Call it only after the caller has been authorized to see the owning record: private media comes back as a
 * short-lived signed GET URL, public media as its permanent CDN URL.
 */
public interface MediaViewUrlResolver {

    String toViewUrl(String storedReference);

    default List<String> toViewUrls(List<String> storedReferences) {
        if (storedReferences == null) {
            return List.of();
        }
        return storedReferences.stream().map(this::toViewUrl).toList();
    }

    /**
     * Resolver that returns references unchanged; for mappers built outside the Spring context (unit tests).
     */
    static MediaViewUrlResolver passThrough() {
        return new PassThrough();
    }

    class PassThrough implements MediaViewUrlResolver {
        @Override
        public String toViewUrl(String storedReference) {
            return storedReference;
        }
    }
}
