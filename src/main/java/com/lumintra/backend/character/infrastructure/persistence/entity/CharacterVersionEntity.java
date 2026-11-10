package com.lumintra.backend.character.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "character_version")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "version_id")
    private Long versionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_id", nullable = false)
    private CharacterEntity character;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "preview_gif_url", length = 500)
    private String previewGifUrl;

    @Column(name = "led_binary_url", length = 500)
    private String ledBinaryUrl;

    @Column(name = "frame_count")
    private Integer frameCount;

    @Column(name = "frame_delay_ms")
    private Integer frameDelayMs;

    @Column(name = "byte_size")
    private Integer byteSize;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static CharacterVersionEntity create(
            CharacterEntity character,
            Long productId,
            int versionNumber,
            String previewGifUrl,
            String ledBinaryUrl,
            boolean isCurrent) {
        CharacterVersionEntity entity = new CharacterVersionEntity();
        entity.setCharacter(character);
        entity.setProductId(productId);
        entity.setVersionNumber(versionNumber);
        entity.setPreviewGifUrl(previewGifUrl);
        entity.setLedBinaryUrl(ledBinaryUrl);
        entity.setCurrent(isCurrent);
        return entity;
    }
}
