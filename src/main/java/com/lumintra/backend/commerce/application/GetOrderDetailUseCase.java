package com.lumintra.backend.commerce.application;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.OrderDetailResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;
import com.lumintra.backend.commerce.domain.service.PaymentInstructionPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetOrderDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetOrderDetailUseCase.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProductLookupService productLookupService;
    private final CharacterLookupService characterLookupService;
    private final PaymentInstructionPort paymentInstructionPort;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public OrderDetailResponse execute(Long customerId, Long orderId) {
        OrderEntity order = orderRepository.findByOrderIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> {
                    log.warn("Order not found or access denied [orderId={}] [customerId={}]", orderId, customerId);
                    return new OrderNotFoundException("Order not found.");
                });

        List<OrderItemEntity> items = orderItemRepository.findAllByOrder_OrderIdOrderByOrderItemIdAsc(orderId);

        Set<Long> productIds = items.stream()
                .map(OrderItemEntity::getProductId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, ProductSnapshot> productsById = productIds.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(productIds);

        Set<Long> characterProductIds = productsById.values().stream()
                .filter(product -> product.productType() == ProductType.CHARACTER)
                .map(ProductSnapshot::productId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, String> previewGifUrlsByProductId = characterProductIds.isEmpty()
                ? Collections.emptyMap()
                : characterLookupService.findPreviewGifUrlsByProductIds(characterProductIds);

        OrderDeliveryEntity delivery = orderDeliveryRepository.findByOrder_OrderId(orderId).orElse(null);
        PaymentTransactionEntity payment = paymentTransactionRepository
                .findFirstByOrderIdOrderByCreatedAtDescPaymentIdDesc(orderId)
                .orElse(null);

        Instant now = Instant.now();
        SepayPaymentInstructions paymentInstructions = shouldBuildActivePaymentInstructions(order, payment, now)
                ? paymentInstructionPort.buildInstructions(
                        order.getOrderId(),
                        payment.getTransactionCode(),
                        order.getTotalAmount(),
                        order.getExpiresAt())
                : null;

        OrderDetailResponse response = orderMapper.toOrderDetailResponse(
                order,
                items,
                productsById,
                previewGifUrlsByProductId,
                delivery,
                payment,
                paymentInstructions
        );

        log.info("Order detail retrieved [orderId={}] [customerId={}] [status={}]",
                orderId, customerId, order.getStatus());

        return response;
    }

    private boolean shouldBuildActivePaymentInstructions(
            OrderEntity order,
            PaymentTransactionEntity payment,
            Instant now) {
        return order.getStatus() == OrderStatus.PENDING_PAYMENT
                && order.getExpiresAt() != null
                && order.getExpiresAt().isAfter(now)
                && payment != null
                && payment.getStatus() == PaymentStatus.PENDING;
    }
}
