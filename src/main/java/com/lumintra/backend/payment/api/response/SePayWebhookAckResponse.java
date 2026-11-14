package com.lumintra.backend.payment.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "SePay webhook acknowledgement envelope containing both SePay's top-level success flag and standard API data payload")
public record SePayWebhookAckResponse(
        @Schema(description = "Top-level acknowledgement flag required by SePay webhooks", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean success,

        @Schema(description = "Standard API payload describing the webhook processing outcome", requiredMode = Schema.RequiredMode.REQUIRED)
        SePayWebhookResponse data
) {
    public static SePayWebhookAckResponse ok(SePayWebhookResponse data) {
        return new SePayWebhookAckResponse(true, data);
    }
}
