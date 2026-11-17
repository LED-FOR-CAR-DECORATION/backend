package com.lumintra.backend.commission.infrastructure.persistence.entity;

import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "commission_message",
        indexes = {
                @Index(name = "idx_commission_message_commission_created", columnList = "commission_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CommissionMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "commission_id", nullable = false)
    private Long commissionId;

    @Column(name = "sender_id")
    private Long senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false, length = 40)
    private CommissionMessageSenderRole senderRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private CommissionMessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static CommissionMessageEntity createSystemMessage(Long commissionId, String content) {
        return CommissionMessageEntity.builder()
                .commissionId(commissionId)
                .senderId(null)
                .senderRole(CommissionMessageSenderRole.SYSTEM)
                .messageType(CommissionMessageType.SYSTEM)
                .content(content)
                .build();
    }

    public static CommissionMessageEntity create(
            Long commissionId,
            Long senderId,
            CommissionMessageSenderRole senderRole,
            CommissionMessageType messageType,
            String content,
            String mediaUrl,
            String metadataJson) {
        return CommissionMessageEntity.builder()
                .commissionId(commissionId)
                .senderId(senderId)
                .senderRole(senderRole)
                .messageType(messageType)
                .content(content)
                .mediaUrl(mediaUrl)
                .metadataJson(metadataJson)
                .build();
    }

    public static CommissionMessageEntity createDeliverableCard(
            Long commissionId,
            Long staffId,
            String content,
            String mediaUrl,
            String metadataJson) {
        return create(
                commissionId,
                staffId,
                CommissionMessageSenderRole.STAFF,
                CommissionMessageType.DELIVERABLE_CARD,
                content,
                mediaUrl,
                metadataJson
        );
    }
}
