package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.service.LedInstanceFulfillmentPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemFulfillmentEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemFulfillmentRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UnlinkOrderFulfillmentDeviceUseCase {

    private static final Logger log = LoggerFactory.getLogger(UnlinkOrderFulfillmentDeviceUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderItemFulfillmentRepository orderItemFulfillmentRepository;
    private final LedInstanceFulfillmentPort ledInstanceFulfillmentPort;
    private final LinkOrderFulfillmentDeviceUseCase linkOrderFulfillmentDeviceUseCase;

    @Transactional
    public void execute(
            String authenticatedSubject,
            Long orderId,
            Long instanceId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));

        if (order.getStatus() != OrderStatus.PROCESSING && order.getStatus() != OrderStatus.PARTIALLY_FULFILLED) {
            throw new OrderStatusTransitionException(
                    "Order must be in PROCESSING or PARTIALLY_FULFILLED status to unlink fulfillment devices. Current status: "
                            + order.getStatus()
            );
        }

        OrderItemFulfillmentEntity fulfillment = orderItemFulfillmentRepository
                .findByOrderItem_Order_OrderIdAndInstanceId(orderId, instanceId)
                .orElseThrow(() -> new OrderFulfillmentException(
                        HttpStatus.NOT_FOUND,
                        "FULFILLMENT_RECORD_NOT_FOUND",
                        "No fulfillment link found for instance " + instanceId + " in order " + orderId + "."
                ));

        orderItemFulfillmentRepository.delete(fulfillment);
        ledInstanceFulfillmentPort.releaseToStock(instanceId);

        long totalOrderFulfilled = orderItemFulfillmentRepository.countByOrderItem_Order_OrderId(orderId);
        long totalOrderRequired = linkOrderFulfillmentDeviceUseCase.calculateTotalRequiredLedUnits(orderId);

        if (totalOrderFulfilled < totalOrderRequired && order.getStatus() != OrderStatus.PARTIALLY_FULFILLED) {
            order.setStatus(OrderStatus.PARTIALLY_FULFILLED);
            orderRepository.save(order);
        }

        log.info("LED instance unlinked from order [orderId={}] [instanceId={}] [employeeAccountId={}] [remainingFulfilled={}/{}]",
                orderId, instanceId, employeeAccountId, totalOrderFulfilled, totalOrderRequired);
    }
}
