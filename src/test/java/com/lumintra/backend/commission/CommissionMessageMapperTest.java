package com.lumintra.backend.commission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommissionMessageMapperTest {

    private static final String KEY = "commissions/105/drafts/123e4567-e89b-12d3-a456-426614174000-v1.gif";

    /** Fake resolver: prefixes private keys so the test can see what was resolved. */
    private final MediaViewUrlResolver signingResolver =
            ref -> ref != null && ref.startsWith("commissions/") ? "https://signed.example/" + ref : ref;

    @Test
    @DisplayName("MED-02 [N]: Chat image mediaUrl is resolved to a viewable URL")
    void toResponse_resolvesMediaUrl() {
        CommissionMessageEntity entity = CommissionMessageEntity.create(
                105L, 42L, CommissionMessageSenderRole.CUSTOMER, CommissionMessageType.IMAGE, "", KEY, null);

        CommissionMessageResponse response = new CommissionMessageMapper(signingResolver).toResponse(entity);

        assertThat(response.mediaUrl()).isEqualTo("https://signed.example/" + KEY);
    }

    @Test
    @DisplayName("MED-02 [N]: Deliverable card metadata imageUrl is resolved; other metadata is preserved")
    void toResponse_resolvesMetadataImageUrl() throws Exception {
        String metadata = "{\"submissionId\":9,\"versionNumber\":2,\"imageUrl\":\"" + KEY + "\",\"autoCompleteAt\":\"2026-11-27T00:00:00Z\"}";
        CommissionMessageEntity entity = CommissionMessageEntity.createDeliverableCard(105L, 7L, "Preview", KEY, metadata);

        CommissionMessageResponse response = new CommissionMessageMapper(signingResolver).toResponse(entity);

        JsonNode node = new ObjectMapper().readTree(response.metadataJson());
        assertThat(node.get("imageUrl").asText()).isEqualTo("https://signed.example/" + KEY);
        assertThat(node.get("submissionId").asLong()).isEqualTo(9L);
        assertThat(node.get("versionNumber").asInt()).isEqualTo(2);
        assertThat(node.get("autoCompleteAt").asText()).isEqualTo("2026-11-27T00:00:00Z");
    }

    @Test
    @DisplayName("MED-02 [B]: Metadata without imageUrl, or malformed metadata, is returned untouched")
    void toResponse_leavesOtherMetadataUntouched() {
        CommissionMessageMapper mapper = new CommissionMessageMapper(signingResolver);
        String plain = "{\"revisionRequestId\":3}";
        String malformed = "{\"imageUrl\": ";

        CommissionMessageEntity a = CommissionMessageEntity.create(
                105L, 7L, CommissionMessageSenderRole.STAFF, CommissionMessageType.TEXT, "hi", null, plain);
        CommissionMessageEntity b = CommissionMessageEntity.create(
                105L, 7L, CommissionMessageSenderRole.STAFF, CommissionMessageType.TEXT, "hi", null, malformed);

        assertThat(mapper.toResponse(a).metadataJson()).isEqualTo(plain);
        assertThat(mapper.toResponse(b).metadataJson()).isEqualTo(malformed);
        assertThat(mapper.toResponse(a).mediaUrl()).isNull();
    }
}
