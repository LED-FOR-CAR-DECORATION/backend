package com.lumintra.backend.media;

import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.MediaKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaKeyGeneratorTest {

    private final MediaKeyGenerator generator = new MediaKeyGenerator();

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_REFERENCE produces scoped user folder")
    void generateObjectKey_commissionReference_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.COMMISSION_REFERENCE, "42", null, "sketch.png");
        assertThat(key).startsWith("commissions/references/42/");
        assertThat(key).endsWith("-sketch.png");
    }

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_CHAT produces scoped commission chat folder")
    void generateObjectKey_commissionChat_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.COMMISSION_CHAT, "42", 101L, "photo.jpg");
        assertThat(key).startsWith("commissions/101/chat/");
        assertThat(key).endsWith("-photo.jpg");
    }

    @Test
    @DisplayName("MED-01 [N]: COMMISSION_DRAFT produces scoped commission drafts folder")
    void generateObjectKey_commissionDraft_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.COMMISSION_DRAFT, "42", 101L, "draft_v1.gif");
        assertThat(key).startsWith("commissions/101/drafts/");
        assertThat(key).endsWith("-draft_v1.gif");
    }

    @Test
    @DisplayName("MED-01 [N]: WARRANTY_EVIDENCE produces scoped device folder")
    void generateObjectKey_warrantyEvidence_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.WARRANTY_EVIDENCE, "42", 2001L, "broken_screen.png");
        assertThat(key).startsWith("warranty/2001/");
        assertThat(key).endsWith("-broken_screen.png");
    }

    @Test
    @DisplayName("MED-01 [N]: CHARACTER_ASSET produces scoped character creator folder")
    void generateObjectKey_characterAsset_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.CHARACTER_ASSET, "42", null, "anim.gif");
        assertThat(key).startsWith("characters/42/");
        assertThat(key).endsWith("-anim.gif");
    }

    @Test
    @DisplayName("MED-01 [N]: STORE_PRODUCT produces store products folder")
    void generateObjectKey_storeProduct_shouldFormatCorrectly() {
        String key = generator.generateObjectKey(MediaPurpose.STORE_PRODUCT, "42", null, "product_thumb.webp");
        assertThat(key).startsWith("store/products/");
        assertThat(key).endsWith("-product_thumb.webp");
    }

    @Test
    @DisplayName("MED-01 [A]: Path traversal attempts are sanitized")
    void sanitizeFileName_whenPathTraversal_shouldNeutralize() {
        String malicious = "../../../etc/passwd.png";
        String clean = generator.sanitizeFileName(malicious);
        assertThat(clean).doesNotContain("..");
        assertThat(clean).doesNotContain("/");
        assertThat(clean).doesNotContain("\\");
        assertThat(clean).isEqualTo("passwd.png");
    }

    @Test
    @DisplayName("MED-01 [A]: Dangerous characters replaced with safe hyphen")
    void sanitizeFileName_whenSpecialChars_shouldReplace() {
        String special = "my image (final) #1!.gif";
        String clean = generator.sanitizeFileName(special);
        assertThat(clean).doesNotContain(" ");
        assertThat(clean).doesNotContain("(");
        assertThat(clean).doesNotContain(")");
        assertThat(clean).doesNotContain("#");
        assertThat(clean).doesNotContain("!");
        assertThat(clean).endsWith(".gif");
    }
}
