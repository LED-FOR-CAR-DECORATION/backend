package com.lumintra.backend.media.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Default {@link MediaViewUrlResolver}: signs private media (bare keys and legacy URLs alike) against the private
 * bucket and leaves public media on its permanent URL.
 */
@Component
public class SignedMediaViewUrlResolver implements MediaViewUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(SignedMediaViewUrlResolver.class);

    private final MediaReferenceParser mediaReferenceParser;
    private final StorageSignedUrlService storageSignedUrlService;
    private final Duration readExpiration;

    public SignedMediaViewUrlResolver(
            MediaReferenceParser mediaReferenceParser,
            StorageSignedUrlService storageSignedUrlService,
            @Value("${app.firebase.signed-url.read-expiration-minutes:15}") int readExpirationMinutes) {
        this.mediaReferenceParser = mediaReferenceParser;
        this.storageSignedUrlService = storageSignedUrlService;
        this.readExpiration = Duration.ofMinutes(readExpirationMinutes > 0 ? readExpirationMinutes : 15);
    }

    @Override
    public String toViewUrl(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            return storedReference;
        }
        Optional<String> objectKey = mediaReferenceParser.extractObjectKey(storedReference);
        if (objectKey.isEmpty()) {
            // Not one of our media hosts (e.g. seeded placeholder) - nothing to sign.
            return storedReference;
        }

        String key = objectKey.get();
        if (MediaKeyGenerator.isPrivateKey(key)) {
            try {
                return storageSignedUrlService.generateV4SignedGetUrl(key, readExpiration);
            } catch (RuntimeException ex) {
                // Degrade to a missing image rather than failing the whole response.
                log.warn("Could not sign private media read URL [objectKey={}]: {}", key, ex.getMessage());
                return null;
            }
        }
        return storedReference.contains("://") ? storedReference : storageSignedUrlService.publicUrlFor(key);
    }
}
