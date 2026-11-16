package com.lumintra.backend.media;

import com.lumintra.backend.media.domain.service.MediaUrlValidator;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaUrlValidatorTest {

    private final MediaUrlValidator validator = new MediaUrlValidator(
            "https://cdn.lumintra.com",
            "lumintra-cloud-storage.appspot.com"
    );

    @Test
    @DisplayName("MED-01 [N]: Valid Cloudflare CDN URL is accepted")
    void isValidMediaUrl_whenCdnUrl_shouldReturnTrue() {
        String url = "https://cdn.lumintra.com/characters/4/123-avatar.gif";
        assertThat(validator.isValidMediaUrl(url)).isTrue();
        assertThatCode(() -> validator.validateMediaUrl(url)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [N]: Valid Firebase Storage path URL is accepted")
    void isValidMediaUrl_whenGcsPathUrl_shouldReturnTrue() {
        String url = "https://storage.googleapis.com/lumintra-cloud-storage.appspot.com/characters/4/123-avatar.gif";
        assertThat(validator.isValidMediaUrl(url)).isTrue();
        assertThatCode(() -> validator.validateMediaUrl(url)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [N]: Valid bucket-prefixed GCS URL is accepted")
    void isValidMediaUrl_whenGcsBucketPrefixUrl_shouldReturnTrue() {
        String url = "https://lumintra-cloud-storage.appspot.com.storage.googleapis.com/characters/4/123-avatar.gif";
        assertThat(validator.isValidMediaUrl(url)).isTrue();
        assertThatCode(() -> validator.validateMediaUrl(url)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MED-01 [A]: External unverified host is rejected")
    void isValidMediaUrl_whenExternalHost_shouldReturnFalse() {
        String url = "https://evil-attacker.com/malicious.gif";
        assertThat(validator.isValidMediaUrl(url)).isFalse();
        assertThatThrownBy(() -> validator.validateMediaUrl(url))
                .isInstanceOf(InvalidMediaUrlException.class)
                .hasMessageContaining("Submitted media URL is invalid");
    }

    @Test
    @DisplayName("MED-01 [A]: Insecure HTTP scheme is rejected")
    void isValidMediaUrl_whenInsecureHttp_shouldReturnFalse() {
        String url = "http://cdn.lumintra.com/characters/4/123-avatar.gif";
        assertThat(validator.isValidMediaUrl(url)).isFalse();
        assertThatThrownBy(() -> validator.validateMediaUrl(url))
                .isInstanceOf(InvalidMediaUrlException.class);
    }

    @Test
    @DisplayName("MED-01 [A]: Null or empty string is rejected")
    void isValidMediaUrl_whenNullOrBlank_shouldReturnFalse() {
        assertThat(validator.isValidMediaUrl(null)).isFalse();
        assertThat(validator.isValidMediaUrl("   ")).isFalse();
        assertThatThrownBy(() -> validator.validateMediaUrl(null))
                .isInstanceOf(InvalidMediaUrlException.class);
    }

    @Test
    @DisplayName("MED-01 [A]: GCS URL with unauthorized bucket is rejected")
    void isValidMediaUrl_whenGcsWrongBucket_shouldReturnFalse() {
        String url = "https://storage.googleapis.com/hacker-bucket/malicious.gif";
        assertThat(validator.isValidMediaUrl(url)).isFalse();
        assertThatThrownBy(() -> validator.validateMediaUrl(url))
                .isInstanceOf(InvalidMediaUrlException.class);
    }
}
