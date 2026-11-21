package com.lumintra.backend.commission.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maps chat messages to responses. Media references (mediaUrl and the deliverable card's metadata imageUrl) are
 * resolved to viewable URLs here, so every REST and WebSocket consumer receives short-lived signed URLs.
 */
@Component
public class CommissionMessageMapper {

    private static final String METADATA_IMAGE_URL = "imageUrl";
    private static final ObjectMapper METADATA_JSON = new ObjectMapper()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    private static final TypeReference<LinkedHashMap<String, Object>> METADATA_TYPE = new TypeReference<>() {};

    private final MediaViewUrlResolver mediaViewUrlResolver;

    /** Pass-through media resolution; for use outside the Spring context (unit tests). */
    public CommissionMessageMapper() {
        this(MediaViewUrlResolver.passThrough());
    }

    @Autowired
    public CommissionMessageMapper(MediaViewUrlResolver mediaViewUrlResolver) {
        this.mediaViewUrlResolver = mediaViewUrlResolver;
    }

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
                .mediaUrl(mediaViewUrlResolver.toViewUrl(entity.getMediaUrl()))
                .metadataJson(resolveMetadataMedia(entity.getMetadataJson()))
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String resolveMetadataMedia(String metadataJson) {
        if (metadataJson == null || !metadataJson.contains("\"" + METADATA_IMAGE_URL + "\"")) {
            return metadataJson;
        }
        try {
            Map<String, Object> metadata = METADATA_JSON.readValue(metadataJson, METADATA_TYPE);
            if (!(metadata.get(METADATA_IMAGE_URL) instanceof String storedReference)) {
                return metadataJson;
            }
            String viewUrl = mediaViewUrlResolver.toViewUrl(storedReference);
            if (Objects.equals(viewUrl, storedReference)) {
                return metadataJson;
            }
            metadata.put(METADATA_IMAGE_URL, viewUrl);
            return METADATA_JSON.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return metadataJson;
        }
    }
}
