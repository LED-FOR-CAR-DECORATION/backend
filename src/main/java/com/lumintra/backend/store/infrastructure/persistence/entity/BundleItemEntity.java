package com.lumintra.backend.store.infrastructure.persistence.entity;

import com.lumintra.backend.store.domain.model.BundleItemType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "bundle_item")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BundleItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bundle_item_id")
    private Long bundleItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bundle_id", nullable = false)
    private BundleEntity bundle;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private BundleItemType itemType;

    @Column(name = "model_id")
    private Long modelId;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static BundleItemEntity createLedItem(BundleEntity bundle, Long modelId, int quantity) {
        BundleItemEntity entity = new BundleItemEntity();
        entity.setBundle(bundle);
        entity.setItemType(BundleItemType.LED);
        entity.setModelId(modelId);
        entity.setQuantity(quantity);
        return entity;
    }

    public static BundleItemEntity createCharacterItem(BundleEntity bundle, Long versionId, int quantity) {
        BundleItemEntity entity = new BundleItemEntity();
        entity.setBundle(bundle);
        entity.setItemType(BundleItemType.CHARACTER);
        entity.setVersionId(versionId);
        entity.setQuantity(quantity);
        return entity;
    }
}
