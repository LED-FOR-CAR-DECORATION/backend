package com.lumintra.backend.commerce.application;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.commerce.OrderNotFoundException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetStaffOrderDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetStaffOrderDetailUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProductLookupService productLookupService;
    private final CharacterLookupService characterLookupService;
    private final CustomerLookupService customerLookupService;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public StaffOrderDetailResponse execute(String authenticatedSubject, Long orderId) {
        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Staff order detail not found [orderId={}] [employeeAccountId={}]",
                            orderId, employeeAccountId);
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
        CustomerProfileSnapshot customer = customerLookupService
                .findProfileByCustomerId(order.getCustomerId())
                .orElse(null);

        StaffOrderDetailResponse response = orderMapper.toStaffOrderDetailResponse(
                order,
                customer,
                items,
                productsById,
                previewGifUrlsByProductId,
                delivery,
                payment
        );

        log.info("Staff order detail retrieved [orderId={}] [employeeAccountId={}] [status={}]",
                orderId, employeeAccountId, order.getStatus());

        return response;
    }
}
