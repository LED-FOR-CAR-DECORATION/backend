package com.lumintra.backend.store.infrastructure.persistence.entity;

import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gallery_urls")
    private List<String> galleryUrls = new ArrayList<>();

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ProductStatus status;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "product_tag",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<TagEntity> tags = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public static ProductEntity create(String productName, ProductType productType, BigDecimal basePrice,
                                       String description, String thumbnailUrl, List<String> galleryUrls,
                                       ProductStatus status, Set<TagEntity> tags) {
        ProductEntity entity = new ProductEntity();
        entity.setProductName(productName);
        entity.setProductType(productType);
        entity.setBasePrice(basePrice);
        entity.setDescription(description);
        entity.setThumbnailUrl(thumbnailUrl);
        entity.setGalleryUrls(galleryUrls != null ? galleryUrls : new ArrayList<>());
        entity.setStatus(status != null ? status : ProductStatus.UNPUBLISHED);
        entity.setTags(tags != null ? tags : new HashSet<>());
        return entity;
    }

    public static ProductEntity of(Long productId, String productName, ProductType productType, BigDecimal basePrice,
                                  String description, String thumbnailUrl, List<String> galleryUrls,
                                  ProductStatus status, Set<TagEntity> tags, Instant createdAt) {
        ProductEntity entity = new ProductEntity();
        entity.setProductId(productId);
        entity.setProductName(productName);
        entity.setProductType(productType);
        entity.setBasePrice(basePrice);
        entity.setDescription(description);
        entity.setThumbnailUrl(thumbnailUrl);
        entity.setGalleryUrls(galleryUrls != null ? galleryUrls : new ArrayList<>());
        entity.setStatus(status);
        entity.setTags(tags != null ? tags : new HashSet<>());
        entity.setCreatedAt(createdAt);
        return entity;
    }
}
