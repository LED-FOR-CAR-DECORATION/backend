package com.lumintra.backend.media;

import com.lumintra.backend.media.api.request.CreateSignedUploadUrlRequest;
import com.lumintra.backend.media.api.response.SignedUploadUrlResponse;
import com.lumintra.backend.media.application.CreateSignedUploadUrlUseCase;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.MediaAccessControlService;
import com.lumintra.backend.media.domain.service.MediaKeyGenerator;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.InvalidMediaRequestException;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateSignedUploadUrlUseCaseTest {

    @Mock
    private MediaAccessControlService mediaAccessControlService;

    @Mock
    private MediaKeyGenerator mediaKeyGenerator;

    @Mock
    private StorageSignedUrlService storageSignedUrlService;

    @InjectMocks
    private CreateSignedUploadUrlUseCase createSignedUploadUrlUseCase;

    @Test
    @DisplayName("MED-01 [N]: Valid upload request -> generates signed URL and returns response")
    void execute_whenValidRequest_shouldReturnSignedUrlResponse() {
        String subject = "4";
        Set<String> roles = Set.of("DESIGNER");
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "avatar.gif",
                "image/gif",
                65536L,
                MediaPurpose.CHARACTER_ASSET,
                null
        );

        String generatedKey = "characters/4/123e4567-avatar.gif";
        StorageSignedUrlService.SignedUploadUrlResult signResult = new StorageSignedUrlService.SignedUploadUrlResult(
                "https://storage.googleapis.com/lumintra/characters/4/123e4567-avatar.gif?signed",
                "https://cdn.lumintra.com/characters/4/123e4567-avatar.gif",
                generatedKey
        );

        when(mediaKeyGenerator.generateObjectKey(MediaPurpose.CHARACTER_ASSET, subject, null, "avatar.gif"))
                .thenReturn(generatedKey);
        when(storageSignedUrlService.generateV4SignedPutUrl(eq(generatedKey), eq("image/gif"), eq(65536L), any(Duration.class)))
                .thenReturn(signResult);

        SignedUploadUrlResponse response = createSignedUploadUrlUseCase.execute(subject, roles, request);

        assertThat(response).isNotNull();
        assertThat(response.signedUploadUrl()).isEqualTo(signResult.signedUploadUrl());
        assertThat(response.publicUrl()).isEqualTo(signResult.publicUrl());
        assertThat(response.objectKey()).isEqualTo(generatedKey);
        assertThat(response.expiresAt()).isNotNull();

        verify(mediaAccessControlService).verifyAccess(subject, roles, MediaPurpose.CHARACTER_ASSET, null);
    }

    @Test
    @DisplayName("MED-01 [A]: Null request -> throws InvalidMediaRequestException")
    void execute_whenNullRequest_shouldThrowException() {
        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute("4", Set.of("CUSTOMER"), null))
                .isInstanceOf(InvalidMediaRequestException.class)
                .hasMessage("Request body must not be null.");

        verify(mediaAccessControlService, never()).verifyAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MED-01 [A]: Unsupported MIME type -> throws InvalidMediaRequestException")
    void execute_whenUnsupportedMime_shouldThrowException() {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "doc.pdf",
                "application/pdf",
                1024L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute("4", Set.of("CUSTOMER"), request))
                .isInstanceOf(InvalidMediaRequestException.class)
                .hasMessageContaining("Invalid MIME content-type");

        verify(mediaAccessControlService, never()).verifyAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MED-01 [A]: SVG+XML MIME type -> throws InvalidMediaRequestException")
    void execute_whenSvgXmlMime_shouldThrowException() {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "vector.svg",
                "image/svg+xml",
                1024L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute("4", Set.of("CUSTOMER"), request))
                .isInstanceOf(InvalidMediaRequestException.class)
                .hasMessageContaining("Invalid MIME content-type");

        verify(mediaAccessControlService, never()).verifyAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MED-01 [A]: File size out of range -> throws InvalidMediaRequestException")
    void execute_whenFileSizeOutOfRange_shouldThrowException() {
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "big.png",
                "image/png",
                10_485_761L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute("4", Set.of("CUSTOMER"), request))
                .isInstanceOf(InvalidMediaRequestException.class)
                .hasMessageContaining("fileSize must be between 1 and 10485760 bytes.");

        verify(mediaAccessControlService, never()).verifyAccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("MED-01 [A]: Access denied by ACL -> propagates AccessDeniedException")
    void execute_whenAccessDenied_shouldPropagateException() {
        String subject = "4";
        Set<String> roles = Set.of("CUSTOMER");
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "product.png",
                "image/png",
                1024L,
                MediaPurpose.STORE_PRODUCT,
                null
        );

        doThrow(new AccessDeniedException("Only managers and admins are authorized to upload store product media."))
                .when(mediaAccessControlService).verifyAccess(subject, roles, MediaPurpose.STORE_PRODUCT, null);

        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute(subject, roles, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only managers and admins");

        verify(storageSignedUrlService, never()).generateV4SignedPutUrl(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("MED-01 [A]: Storage failure -> propagates StorageServiceException")
    void execute_whenStorageFails_shouldPropagateException() {
        String subject = "4";
        Set<String> roles = Set.of("CUSTOMER");
        CreateSignedUploadUrlRequest request = new CreateSignedUploadUrlRequest(
                "sketch.png",
                "image/png",
                1024L,
                MediaPurpose.COMMISSION_REFERENCE,
                null
        );

        when(mediaKeyGenerator.generateObjectKey(any(), any(), any(), any())).thenReturn("key");
        when(storageSignedUrlService.generateV4SignedPutUrl(any(), any(), anyLong(), any()))
                .thenThrow(new StorageServiceException("GCS connection timeout"));

        assertThatThrownBy(() -> createSignedUploadUrlUseCase.execute(subject, roles, request))
                .isInstanceOf(StorageServiceException.class)
                .hasMessageContaining("GCS connection timeout");
    }
}
