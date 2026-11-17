package com.lumintra.backend.commission.infrastructure.persistence.mapper;

import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import org.springframework.stereotype.Component;

@Component
public class CommissionMessageMapper {

    public CommissionMessageResponse toResponse(CommissionMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        return CommissionMessageResponse.builder()
                .messageId(entity.getMessageId())
                .commissionId(entity.getCommissionId())
                .senderId(entity.getSenderId())
                .senderRole(entity.getSenderRole())
                .messageType(entity.getMessageType())
                .content(entity.getContent())
                .mediaUrl(entity.getMediaUrl())
                .metadataJson(entity.getMetadataJson())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}