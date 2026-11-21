package com.lumintra.backend.media;

import com.lumintra.backend.media.domain.service.MediaReferenceParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaReferenceParserTest {

    private static final String KEY = "commissions/105/chat/123e4567-e89b-12d3-a456-426614174000-photo.png";

    private final MediaReferenceParser parser =
            new MediaReferenceParser("https://cdn.lumintra.com", "public-bucket", "private-bucket");

    @Test
    @DisplayName("MED-02 [N]: Bare object key is returned as-is")
    void extract_bareKey() {
        assertThat(parser.extractObjectKey(KEY)).contains(KEY);
    }

    @Test
    @DisplayName("MED-02 [N]: Legacy CDN URL resolves to its object key")
    void extract_cdnUrl() {
        assertThat(parser.extractObjectKey("https://cdn.lumintra.com/" + KEY)).contains(KEY);
    }

    @Test
    @DisplayName("MED-02 [N]: storage.googleapis.com path URL for either bucket resolves to its object key")
    void extract_gcsPathUrl() {
        assertThat(parser.extractObjectKey("https://storage.googleapis.com/public-bucket/" + KEY)).contains(KEY);
        assertThat(parser.extractObjectKey("https://storage.googleapis.com/private-bucket/" + KEY)).contains(KEY);
    }

    @Test
    @DisplayName("MED-02 [N]: Signed URL query string is ignored")
    void extract_signedUrlIgnoresQuery() {
        assertThat(parser.extractObjectKey(
                "https://storage.googleapis.com/private-bucket/" + KEY + "?X-Goog-Signature=abc&X-Goog-Expires=900"))
                .contains(KEY);
    }

    @Test
    @DisplayName("MED-02 [N]: Bucket subdomain URL resolves to its object key")
    void extract_bucketSubdomainUrl() {
        assertThat(parser.extractObjectKey("https://private-bucket.storage.googleapis.com/" + KEY)).contains(KEY);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://evil.com/" + KEY,
            "http://cdn.lumintra.com/" + KEY,
            "https://storage.googleapis.com/other-bucket/" + KEY,
            "https://cdn.lumintra.com/",
            "commissions/105/../106/chat/x.png",
            "/commissions/105/chat/x.png",
            "commissions//chat/x.png",
            "commissions\\105\\chat\\x.png"
    })
    @DisplayName("MED-02 [A]: Foreign hosts, plain http, unknown buckets and unsafe keys are rejected")
    void extract_rejectsUnsafeOrForeign(String reference) {
        assertThat(parser.extractObjectKey(reference)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("MED-02 [B]: Null or blank reference yields empty")
    void extract_blank(String reference) {
        assertThat(parser.extractObjectKey(reference)).isEmpty();
    }
}
