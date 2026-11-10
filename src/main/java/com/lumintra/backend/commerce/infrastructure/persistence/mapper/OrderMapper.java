package com.lumintra.backend.commerce.infrastructure.persistence.mapper;

import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public CheckoutResponse toCheckoutResponse(OrderEntity order, PaymentIntentResult paymentIntent) {
        if (order == null) {
            return null;
        }
        return new CheckoutResponse(
                order.getOrderId(),
                paymentIntent != null ? paymentIntent.paymentId() : null,
                paymentIntent != null ? paymentIntent.gateway() : null,
                order.getTotalAmount(),
                order.getStatus(),
                order.getExpiresAt()
        );
    }
}
