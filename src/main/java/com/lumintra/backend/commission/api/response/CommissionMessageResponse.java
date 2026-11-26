package com.lumintra.backend.commission.api.response;

import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Schema(description = "Single chat message or workflow event card inside a commission chat room")
@Builder
public record CommissionMessageResponse(
        @Schema(description = "Unique message identifier", example = "1201")
        Long messageId,

        @Schema(description = "Parent commission request identifier", example = "105")
        Long commissionId,

        @Schema(description = "Sender account/employee ID (`null` for `SYSTEM` messages)", example = "42", nullable = true)
        Long senderId,

        @Schema(description = "Role of the message sender (`SYSTEM`, `CUSTOMER`, `STAFF`)", example = "CUSTOMER")
        CommissionMessageSenderRole senderRole,

        @Schema(description = "Message payload type (`SYSTEM`, `TEXT`, `IMAGE`, `DELIVERABLE_CARD`, `EXTRA_FEE_CARD`)", example = "TEXT")
        CommissionMessageType messageType,

        @Schema(description = "Text body or caption", example = "Could we make the neon outline slightly brighter cyan?")
        String content,

        @Schema(description = "Attached image URL for `IMAGE` messages", example = "https://cdn.lumintra.com/commissions/chat/105/ref-02.png", nullable = true)
        String mediaUrl,

        @Schema(description = "Structured JSON payload for `DELIVERABLE_CARD` or `EXTRA_FEE_CARD` messages", nullable = true)
        String metadataJson,

        @Schema(description = "ISO-8601 UTC timestamp when the message was persisted", example = "2026-11-17T10:35:00Z")
        Instant createdAt
) {}