package com.lumintra.backend.media;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.media.infrastructure.integration.GoogleCloudStorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCloudStorageSignedUrlServiceTest {

    @Mock
    private Storage storage;

    private GoogleCloudStorageSignedUrlService service;

    @BeforeEach
    void setUp() {
        service = new GoogleCloudStorageSignedUrlService(storage);
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(service, "cdnBaseUrl", "https://cdn.lumintra.com");
    }

    @Test
    @DisplayName("MED-01 [N]: Generates V4 signed PUT URL and constructs CDN public URL")
    void generateV4SignedPutUrl_whenValid_shouldReturnResult() throws Exception {
        URL mockUrl = URI.create("https://storage.googleapis.com/test-bucket/characters/4/test.gif?signed=true").toURL();

        when(storage.signUrl(
                any(BlobInfo.class),
                anyLong(),
                eq(TimeUnit.MINUTES),
                any(Storage.SignUrlOption.class),
                any(Storage.SignUrlOption.class),
                any(Storage.SignUrlOption.class)
        )).thenReturn(mockUrl);

        StorageSignedUrlService.SignedUploadUrlResult result = service.generateV4SignedPutUrl(
                "characters/4/test.gif",
                "image/gif",
                65536L,
                Duration.ofMinutes(15)
        );

        assertThat(result).isNotNull();
        assertThat(result.signedUploadUrl()).isEqualTo(mockUrl.toString());
        assertThat(result.publicUrl()).isEqualTo("https://cdn.lumintra.com/characters/4/test.gif");
        assertThat(result.objectKey()).isEqualTo("characters/4/test.gif");

        verify(storage).signUrl(any(BlobInfo.class), eq(15L), eq(TimeUnit.MINUTES), any(), any(), any());
    }

    @Test
    @DisplayName("MED-01 [N]: Fallback to storage.googleapis.com when CDN_BASE_URL is blank")
    void generateV4SignedPutUrl_whenCdnBaseUrlBlank_shouldFallbackToGcs() throws Exception {
        ReflectionTestUtils.setField(service, "cdnBaseUrl", "");
        URL mockUrl = URI.create("https://storage.googleapis.com/test-bucket/store/products/thumb.png?signed=true").toURL();

        when(storage.signUrl(any(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        StorageSignedUrlService.SignedUploadUrlResult result = service.generateV4SignedPutUrl(
                "store/products/thumb.png",
                "image/png",
                1024L,
                Duration.ofMinutes(15)
        );

        assertThat(result.publicUrl()).isEqualTo("https://storage.googleapis.com/test-bucket/store/products/thumb.png");
    }

    @Test
    @DisplayName("MED-01 [A]: Storage signing failure throws StorageServiceException")
    void generateV4SignedPutUrl_whenSigningFails_shouldThrowStorageServiceException() {
        when(storage.signUrl(any(), anyLong(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("Signing key unavailable"));

        assertThatThrownBy(() -> service.generateV4SignedPutUrl("key", "image/png", 1024L, Duration.ofMinutes(15)))
                .isInstanceOf(StorageServiceException.class)
                .hasMessageContaining("Signing key unavailable");
    }
}
