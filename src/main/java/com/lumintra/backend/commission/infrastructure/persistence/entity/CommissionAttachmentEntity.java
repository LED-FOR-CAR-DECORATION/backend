package com.lumintra.backend.commission.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "commission_attachment",
        indexes = {
                @Index(name = "idx_commission_attachment_commission_order", columnList = "commission_id, display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionAttachmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Long attachmentId;

    @Column(name = "commission_id", nullable = false)
    private Long commissionId;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "caption", length = 255)
    private String caption;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CommissionAttachmentEntity create(
            Long commissionId,
            String imageUrl,
            String caption,
            int displayOrder) {
        return CommissionAttachmentEntity.builder()
                .commissionId(commissionId)
                .imageUrl(imageUrl != null ? imageUrl.trim() : "")
                .caption(caption != null && !caption.isBlank() ? caption.trim() : null)
                .displayOrder(Math.max(0, displayOrder))
                .build();
    }
}
