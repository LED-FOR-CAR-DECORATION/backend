package com.lumintra.backend.media.application;

import com.lumintra.backend.media.api.request.CreateSignedUploadUrlRequest;
import com.lumintra.backend.media.api.response.SignedUploadUrlResponse;
import com.lumintra.backend.media.domain.service.MediaAccessControlService;
import com.lumintra.backend.media.domain.service.MediaKeyGenerator;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.InvalidMediaRequestException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Lean orchestrator UseCase for validating upload requests and issuing short-lived V4 signed upload URLs.
 */
@Service
@RequiredArgsConstructor
public class CreateSignedUploadUrlUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreateSignedUploadUrlUseCase.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/svg+xml"
    );

    private static final long MIN_FILE_SIZE = 1L;
    private static final long MAX_FILE_SIZE = 10_485_760L; // 10MB

    private final MediaAccessControlService mediaAccessControlService;
    private final MediaKeyGenerator mediaKeyGenerator;
    private final StorageSignedUrlService storageSignedUrlService;

    @Value("${lumintra.storage.signed-url.expiration-minutes:15}")
    private int expirationMinutes = 15;

    public SignedUploadUrlResponse execute(String callerSubject, Set<String> callerRoles, CreateSignedUploadUrlRequest request) {
        if (request == null) {
            throw new InvalidMediaRequestException("Request body must not be null.");
        }

        // 1. Validate MIME type
        String contentType = request.contentType() != null ? request.contentType().trim().toLowerCase() : "";
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new InvalidMediaRequestException("Invalid MIME content-type. Allowed: image/jpeg, image/png, image/webp, image/gif, image/svg+xml");
        }

        // 2. Validate file size
        Long fileSize = request.fileSize();
        if (fileSize == null || fileSize < MIN_FILE_SIZE || fileSize > MAX_FILE_SIZE) {
            throw new InvalidMediaRequestException("fileSize must be between 1 and 10485760 bytes.");
        }

        // 3. Verify access control matrix on purpose & targetId
        mediaAccessControlService.verifyAccess(callerSubject, callerRoles, request.purpose(), request.targetId());

        // 4. Sanitize and generate unique scoped object key
        String objectKey = mediaKeyGenerator.generateObjectKey(
                request.purpose(),
                callerSubject,
                request.targetId(),
                request.fileName()
        );

        // 5. Generate short-lived V4 signed PUT URL
        Duration expiration = Duration.ofMinutes(expirationMinutes > 0 ? expirationMinutes : 15);
        StorageSignedUrlService.SignedUploadUrlResult signResult = storageSignedUrlService.generateV4SignedPutUrl(
                objectKey,
                contentType,
                fileSize,
                expiration
        );

        Instant expiresAt = Instant.now().plus(expiration);

        log.info("Signed upload URL issued successfully [purpose={}] [objectKey={}] [callerSubject={}]",
                request.purpose(), objectKey, callerSubject);

        return new SignedUploadUrlResponse(
                signResult.signedUploadUrl(),
                signResult.publicUrl(),
                signResult.objectKey(),
                expiresAt
        );
    }
}
