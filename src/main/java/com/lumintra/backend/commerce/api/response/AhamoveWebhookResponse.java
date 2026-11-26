package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import lombok.Builder;

@Builder
public record AhamoveWebhookResponse(
        String trackingNumber,
        String outcome,
        DeliveryStatus deliveryStatus,
        OrderStatus orderStatus,
        String message
) {
}
