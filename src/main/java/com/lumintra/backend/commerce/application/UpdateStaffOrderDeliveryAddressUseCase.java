package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.UpdateStaffOrderDeliveryAddressRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.shared.exception.commerce.OrderStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UpdateStaffOrderDeliveryAddressUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateStaffOrderDeliveryAddressUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");
    private static final Set<OrderStatus> EDITABLE_ORDER_STATUSES = Set.of(
            OrderStatus.PENDING_PAYMENT,
            OrderStatus.PROCESSING,
            OrderStatus.PARTIALLY_FULFILLED,
            OrderStatus.DELIVERY_FAILED
    );

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final OrderMapper orderMapper;

    @Transactional
    public StaffOrderDetailResponse.DeliveryDetail execute(
            String authenticatedSubject,
            Long orderId,
            UpdateStaffOrderDeliveryAddressRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff delivery address update rejected: order not found [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
                    return new OrderNotFoundException("Order not found.");
                });

        if (!EDITABLE_ORDER_STATUSES.contains(order.getStatus())) {
            log.warn("Staff delivery address update rejected: order status not editable [orderId={}] [status={}] [employeeAccountId={}]",
                    orderId, order.getStatus(), employeeAccountId);
            throw new OrderStatusTransitionException(
                    "Delivery address can only be updated when order is in PENDING_PAYMENT, PROCESSING, PARTIALLY_FULFILLED, or DELIVERY_FAILED status. Current status: "
                            + order.getStatus() + ".");
        }

        OrderDeliveryEntity delivery = orderDeliveryRepository.findByOrder_OrderId(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff delivery address update rejected: no delivery record [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
                    return new OrderStatusTransitionException(
                            "Order does not have a physical delivery record to update.");
                });

        if (delivery.hasActiveShipment()) {
            log.warn("Staff delivery address update rejected: active shipment in progress [orderId={}] [deliveryStatus={}] [employeeAccountId={}]",
                    orderId, delivery.getDeliveryStatus(), employeeAccountId);
            throw new OrderStatusTransitionException(
                    "Delivery address cannot be updated while an active shipment is in status "
                            + delivery.getDeliveryStatus() + ".");
        }

        delivery.setRecipientName(request.recipientName().trim());
        delivery.setRecipientPhone(request.recipientPhone().trim());
        delivery.setFullAddress(request.fullAddress().trim());
        if (request.latitude() != null) {
            delivery.setLatitude(request.latitude().setScale(7, RoundingMode.HALF_UP));
        }
        if (request.longitude() != null) {
            delivery.setLongitude(request.longitude().setScale(7, RoundingMode.HALF_UP));
        }

        OrderDeliveryEntity saved = orderDeliveryRepository.save(delivery);
        log.info("Order delivery address updated by staff [orderId={}] [employeeAccountId={}] [orderStatus={}]",
                orderId, employeeAccountId, order.getStatus());

        return orderMapper.toStaffDeliveryDetail(saved);
    }
}
