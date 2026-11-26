package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.ConfirmOrderDeliveryResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ConfirmOrderDeliveryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ConfirmOrderDeliveryUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public ConfirmOrderDeliveryResponse execute(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> {
                    log.warn("Confirm delivery rejected: order not found or not owned by customer [orderId={}] [customerId={}]",
                            orderId, customerId);
                    return new OrderNotFoundException("Order not found.");
                });

        OrderStatus previousStatus = order.getStatus();
        order.validateCustomerConfirmDelivery();

        Instant updatedAt = Instant.now();
        int updatedRows = orderRepository.updateStatusIfCurrent(
                orderId,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                updatedAt
        );
        if (updatedRows == 0) {
            log.warn("Concurrent customer delivery confirmation conflict [orderId={}] [customerId={}] [expectedStatus={}]",
                    orderId, customerId, previousStatus);
            throw new OrderStatusTransitionException(
                    "Order status changed concurrently and can no longer transition from "
                            + previousStatus + " to " + OrderStatus.COMPLETED + ".");
        }

        log.info("Order delivery confirmed by customer [orderId={}] [customerId={}] [previousStatus={}] [newStatus={}]",
                orderId, customerId, previousStatus, OrderStatus.COMPLETED);

        return orderMapper.toConfirmOrderDeliveryResponse(
                orderId,
                previousStatus,
                OrderStatus.COMPLETED,
                updatedAt
        );
    }
}
