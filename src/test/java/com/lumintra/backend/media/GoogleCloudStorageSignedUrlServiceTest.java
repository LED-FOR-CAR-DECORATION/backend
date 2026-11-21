package com.lumintra.backend.media;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.media.infrastructure.integration.GoogleCloudStorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        ReflectionTestUtils.setField(service, "privateBucketName", "test-private-bucket");
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

    @Test
    @DisplayName("MED-02 [N]: Private purpose key is signed against the private bucket with private cache headers and no public URL")
    void generateV4SignedPutUrl_whenPrivateKey_shouldTargetPrivateBucket() throws Exception {
        String key = "commissions/105/chat/123e4567-e89b-12d3-a456-426614174000-photo.png";
        URL mockUrl = URI.create("https://storage.googleapis.com/test-private-bucket/" + key + "?signed=true").toURL();
        ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        when(storage.signUrl(blobCaptor.capture(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        StorageSignedUrlService.SignedUploadUrlResult result =
                service.generateV4SignedPutUrl(key, "image/png", 1024L, Duration.ofMinutes(15));

        assertThat(blobCaptor.getValue().getBucket()).isEqualTo("test-private-bucket");
        assertThat(blobCaptor.getValue().getCacheControl()).isEqualTo("private, max-age=600");
        assertThat(result.publicUrl()).isNull();
        assertThat(result.objectKey()).isEqualTo(key);
        assertThat(result.uploadHeaders())
                .containsEntry("Content-Type", "image/png")
                .containsEntry("Cache-Control", "private, max-age=600")
                .containsEntry("x-goog-content-length-range", "0,10485760");
    }

    @Test
    @DisplayName("MED-02 [N]: Public purpose key stays in the public bucket with immutable public cache headers")
    void generateV4SignedPutUrl_whenPublicKey_shouldTargetPublicBucket() throws Exception {
        URL mockUrl = URI.create("https://storage.googleapis.com/test-bucket/store/products/x.png?signed=true").toURL();
        ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        when(storage.signUrl(blobCaptor.capture(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        StorageSignedUrlService.SignedUploadUrlResult result =
                service.generateV4SignedPutUrl("store/products/x.png", "image/png", 1024L, Duration.ofMinutes(15));

        assertThat(blobCaptor.getValue().getBucket()).isEqualTo("test-bucket");
        assertThat(result.uploadHeaders()).containsEntry("Cache-Control", "public, max-age=31536000, immutable");
    }

    @Test
    @DisplayName("MED-02 [B]: Private bucket falls back to the public bucket when not configured")
    void generateV4SignedPutUrl_whenPrivateBucketBlank_shouldFallBackToPublicBucket() throws Exception {
        ReflectionTestUtils.setField(service, "privateBucketName", "");
        URL mockUrl = URI.create("https://storage.googleapis.com/test-bucket/warranty/10/x.png?signed=true").toURL();
        ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        when(storage.signUrl(blobCaptor.capture(), anyLong(), any(), any(), any(), any())).thenReturn(mockUrl);

        service.generateV4SignedPutUrl("warranty/10/x.png", "image/png", 1024L, Duration.ofMinutes(15));

        assertThat(blobCaptor.getValue().getBucket()).isEqualTo("test-bucket");
    }

    @Test
    @DisplayName("MED-02 [N]: Signed GET URL is issued against the private bucket")
    void generateV4SignedGetUrl_shouldSignAgainstPrivateBucket() throws Exception {
        String key = "commissions/105/drafts/a.gif";
        URL mockUrl = URI.create("https://storage.googleapis.com/test-private-bucket/" + key + "?X-Goog-Signature=s").toURL();
        ArgumentCaptor<BlobInfo> blobCaptor = ArgumentCaptor.forClass(BlobInfo.class);
        when(storage.signUrl(blobCaptor.capture(), eq(10L), eq(TimeUnit.MINUTES), any(), any())).thenReturn(mockUrl);

        String url = service.generateV4SignedGetUrl(key, Duration.ofMinutes(10));

        assertThat(url).isEqualTo(mockUrl.toString());
        assertThat(blobCaptor.getValue().getBucket()).isEqualTo("test-private-bucket");
        assertThat(blobCaptor.getValue().getName()).isEqualTo(key);
    }

    @Test
    @DisplayName("MED-02 [A]: GET signing failure throws StorageServiceException")
    void generateV4SignedGetUrl_whenSigningFails_shouldThrow() {
        when(storage.signUrl(any(), anyLong(), any(), any(), any())).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> service.generateV4SignedGetUrl("commissions/1/chat/a.png", Duration.ofMinutes(10)))
                .isInstanceOf(StorageServiceException.class);
    }

    @Test
    @DisplayName("MED-02 [N]: Object existence is checked in the private bucket")
    void privateObjectExists_shouldQueryPrivateBucket() {
        when(storage.get(eq(BlobId.of("test-private-bucket", "warranty/10/a.png")), any(Storage.BlobGetOption.class)))
                .thenReturn(org.mockito.Mockito.mock(Blob.class));
        when(storage.get(eq(BlobId.of("test-private-bucket", "warranty/10/missing.png")), any(Storage.BlobGetOption.class)))
                .thenReturn(null);

        assertThat(service.privateObjectExists("warranty/10/a.png")).isTrue();
        assertThat(service.privateObjectExists("warranty/10/missing.png")).isFalse();
    }
}
