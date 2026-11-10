package com.lumintra.backend.commerce.infrastructure.persistence.mapper;

import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public OrderListResponse toOrderListResponse(
            Page<OrderEntity> page,
            Map<Long, Integer> itemCountsByOrderId,
            Map<Long, Long> primaryProductIdByOrderId,
            Map<Long, ProductSnapshot> productsById) {
        if (page == null) {
            return new OrderListResponse(Collections.emptyList(), null);
        }

        List<OrderListResponse.OrderSummaryItem> items = page.getContent().stream()
                .map(order -> toOrderSummaryItem(
                        order,
                        itemCountsByOrderId != null ? itemCountsByOrderId.getOrDefault(order.getOrderId(), 0) : 0,
                        primaryProductIdByOrderId != null && productsById != null
                                ? productsById.get(primaryProductIdByOrderId.get(order.getOrderId()))
                                : null
                ))
                .toList();

        return new OrderListResponse(items, PaginationMeta.from(page));
    }

    public OrderListResponse.OrderSummaryItem toOrderSummaryItem(
            OrderEntity order,
            int totalItems,
            ProductSnapshot primaryProduct) {
        if (order == null) {
            return null;
        }

        Instant visibleExpiresAt = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getExpiresAt()
                : null;

        return OrderListResponse.OrderSummaryItem.builder()
                .orderId(order.getOrderId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .totalItems(totalItems)
                .primaryProductName(primaryProduct != null ? primaryProduct.productName() : "Unavailable Product")
                .primaryProductThumbnail(primaryProduct != null ? primaryProduct.thumbnailUrl() : null)
                .expiresAt(visibleExpiresAt)
                .createdAt(order.getCreatedAt())
                .build();
    }
}
