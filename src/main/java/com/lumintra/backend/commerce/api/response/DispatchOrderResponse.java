package com.lumintra.backend.commerce.api.response;

import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;

@Schema(description = "Updated delivery tracking summary returned after dispatching an order to AhaMove")
@Builder
public record DispatchOrderResponse(
        @Schema(description = "Unique order identifier", example = "102", requiredMode = Schema.RequiredMode.REQUIRED)
        Long orderId,

        @Schema(description = "Updated order lifecycle status", example = "SHIPPING", requiredMode = Schema.RequiredMode.REQUIRED)
        OrderStatus orderStatus,

        @Schema(description = "Updated delivery lifecycle status", example = "ASSIGNED", requiredMode = Schema.RequiredMode.REQUIRED)
        DeliveryStatus deliveryStatus,

        @Schema(description = "Carrier waybill tracking identifier returned by AhaMove", example = "24ABCD", requiredMode = Schema.RequiredMode.REQUIRED)
        String trackingNumber,

        @Schema(description = "Public AhaMove live tracking URL", example = "https://expressstg.ahamove.com/s/241018N83HHH", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String trackingUrl,

        @Schema(description = "Carrier shipping fee quoted by AhaMove in VND", example = "33000.00", nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        BigDecimal carrierFee
) {}
