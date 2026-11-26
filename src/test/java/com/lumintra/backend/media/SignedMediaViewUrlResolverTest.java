package com.lumintra.backend.media;

import com.lumintra.backend.media.domain.service.MediaReferenceParser;
import com.lumintra.backend.media.domain.service.SignedMediaViewUrlResolver;
import com.lumintra.backend.media.domain.service.StorageSignedUrlService;
import com.lumintra.backend.shared.exception.media.StorageServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignedMediaViewUrlResolverTest {

    private static final String DRAFT_KEY = "commissions/105/drafts/123e4567-e89b-12d3-a456-426614174000-v1.gif";
    private static final String SIGNED = "https://storage.googleapis.com/private-bucket/" + DRAFT_KEY + "?X-Goog-Signature=sig";

    @Mock
    private StorageSignedUrlService storageSignedUrlService;

    private SignedMediaViewUrlResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new SignedMediaViewUrlResolver(
                new MediaReferenceParser("https://cdn.lumintra.com", "public-bucket", "private-bucket"),
                storageSignedUrlService,
                10
        );
    }

    @Test
    @DisplayName("MED-02 [N]: Private object key is returned as a signed GET URL with the configured expiry")
    void toViewUrl_privateKeySigned() {
        when(storageSignedUrlService.generateV4SignedGetUrl(DRAFT_KEY, Duration.ofMinutes(10))).thenReturn(SIGNED);

        assertThat(resolver.toViewUrl(DRAFT_KEY)).isEqualTo(SIGNED);
    }

    @Test
    @DisplayName("MED-02 [N]: Legacy CDN URL of a private object is re-signed against the private bucket")
    void toViewUrl_legacyPrivateUrlSigned() {
        when(storageSignedUrlService.generateV4SignedGetUrl(DRAFT_KEY, Duration.ofMinutes(10))).thenReturn(SIGNED);

        assertThat(resolver.toViewUrl("https://cdn.lumintra.com/" + DRAFT_KEY)).isEqualTo(SIGNED);
    }

    @Test
    @DisplayName("MED-02 [N]: Public CDN URL is returned unchanged")
    void toViewUrl_publicUrlUnchanged() {
        String url = "https://cdn.lumintra.com/store/products/123e4567-e89b-12d3-a456-426614174000-thumb.png";

        assertThat(resolver.toViewUrl(url)).isEqualTo(url);
        verify(storageSignedUrlService, never()).generateV4SignedGetUrl(anyString(), any());
    }

    @Test
    @DisplayName("MED-02 [N]: Bare public key is expanded to its CDN URL")
    void toViewUrl_publicKeyExpanded() {
        String key = "characters/4/123e4567-e89b-12d3-a456-426614174000-anim.gif";
        when(storageSignedUrlService.publicUrlFor(key)).thenReturn("https://cdn.lumintra.com/" + key);

        assertThat(resolver.toViewUrl(key)).isEqualTo("https://cdn.lumintra.com/" + key);
    }

    @Test
    @DisplayName("MED-02 [B]: Unknown host, null and blank references pass through")
    void toViewUrl_passThrough() {
        assertThat(resolver.toViewUrl("https://placehold.co/600x400.png")).isEqualTo("https://placehold.co/600x400.png");
        assertThat(resolver.toViewUrl(null)).isNull();
        assertThat(resolver.toViewUrl(" ")).isEqualTo(" ");
    }

    @Test
    @DisplayName("MED-02 [A]: Signing failure degrades to null instead of failing the response")
    void toViewUrl_signingFailureReturnsNull() {
        when(storageSignedUrlService.generateV4SignedGetUrl(anyString(), any()))
                .thenThrow(new StorageServiceException("no key", new RuntimeException()));

        assertThat(resolver.toViewUrl(DRAFT_KEY)).isNull();
    }

    @Test
    @DisplayName("MED-02 [N]: List resolution keeps order and maps null list to empty")
    void toViewUrls_list() {
        when(storageSignedUrlService.generateV4SignedGetUrl(DRAFT_KEY, Duration.ofMinutes(10))).thenReturn(SIGNED);

        assertThat(resolver.toViewUrls(Arrays.asList(DRAFT_KEY, "https://placehold.co/a.png")))
                .containsExactly(SIGNED, "https://placehold.co/a.png");
        assertThat(resolver.toViewUrls(null)).isEqualTo(List.of());
    }
}
