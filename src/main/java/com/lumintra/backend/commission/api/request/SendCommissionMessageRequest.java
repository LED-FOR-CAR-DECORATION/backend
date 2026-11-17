package com.lumintra.backend.commission.api.request;

import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for sending a TEXT or IMAGE message in an active commission chat room")
public record SendCommissionMessageRequest(
        @Schema(description = "Message type (`TEXT` or `IMAGE`). Defaults to `TEXT` if omitted.", example = "TEXT", defaultValue = "TEXT")
        CommissionMessageType messageType,

        @Schema(description = "Message text body (required for `TEXT`, optional caption for `IMAGE`)", example = "Could we make the neon outline slightly brighter cyan?")
        @Size(max = 2000, message = "Message content must not exceed 2000 characters")
        String content,

        @Schema(description = "Uploaded image URL validated via media service (required for `IMAGE`)", example = "https://cdn.lumintra.com/commissions/chat/105/ref-02.png")
        @Size(max = 1000, message = "Media URL must not exceed 1000 characters")
        String mediaUrl
) {
    public SendCommissionMessageRequest {
        messageType = messageType != null ? messageType : CommissionMessageType.TEXT;
        content = (content != null && !content.isBlank()) ? content.trim() : null;
        mediaUrl = (mediaUrl != null && !mediaUrl.isBlank()) ? mediaUrl.trim() : null;
    }
}