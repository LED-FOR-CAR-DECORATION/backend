package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.StaffOrderFilterRequest;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliverySummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderSpecifications;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListStaffOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListStaffOrdersUseCase.class);
    private static final Set<String> ALLOWED_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("orderId")
    );

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ProductLookupService productLookupService;
    private final CustomerLookupService customerLookupService;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public StaffOrderListResponse execute(String authenticatedSubject, StaffOrderFilterRequest request) {
        Long employeeAccountId = internalEmployeeAccessService
                .requireActiveEmployeeAccountId(authenticatedSubject, ALLOWED_ROLES);

        StaffOrderFilterRequest effectiveRequest = request != null ? request : new StaffOrderFilterRequest();
        Pageable pageable = PageRequest.of(effectiveRequest.page(), effectiveRequest.size(), DEFAULT_SORT);

        Page<OrderEntity> orderPage = orderRepository.findAll(
                OrderSpecifications.withStaffFulfillmentFilter(effectiveRequest),
                pageable
        );

        if (orderPage.isEmpty()) {
            StaffOrderListResponse emptyResponse = orderMapper.toStaffOrderListResponse(
                    orderPage,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
            log.info("Staff fulfillment queue retrieved [employeeAccountId={}] [page={}] [size={}] [totalElements={}]",
                    employeeAccountId,
                    emptyResponse.meta().getPage(),
                    emptyResponse.meta().getSize(),
                    emptyResponse.meta().getTotalElements());
            return emptyResponse;
        }

        List<Long> orderIds = orderPage.getContent().stream()
                .map(OrderEntity::getOrderId)
                .toList();

        Set<Long> customerIds = orderPage.getContent().stream()
                .map(OrderEntity::getCustomerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<OrderItemSummaryRow> itemRows = orderItemRepository.findSummaryRowsByOrderIds(orderIds);
        Map<Long, Integer> itemCountsByOrderId = new HashMap<>();
        Map<Long, Long> primaryProductIdByOrderId = new HashMap<>();
        Set<Long> primaryProductIds = new LinkedHashSet<>();

        for (OrderItemSummaryRow row : itemRows) {
            itemCountsByOrderId.merge(row.orderId(), 1, Integer::sum);
            if (primaryProductIdByOrderId.putIfAbsent(row.orderId(), row.productId()) == null
                    && row.productId() != null) {
                primaryProductIds.add(row.productId());
            }
        }

        Map<Long, ProductSnapshot> productsById = primaryProductIds.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(primaryProductIds);

        Map<Long, OrderDeliverySummaryRow> deliveriesByOrderId = orderDeliveryRepository
                .findSummaryRowsByOrderIds(orderIds)
                .stream()
                .collect(Collectors.toMap(
                        OrderDeliverySummaryRow::orderId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        Map<Long, PaymentStatus> paymentStatusesByOrderId = new HashMap<>();
        for (PaymentSummaryRow row : paymentTransactionRepository.findSummaryRowsByOrderIds(orderIds)) {
            if (row.orderId() != null && row.status() != null) {
                paymentStatusesByOrderId.putIfAbsent(row.orderId(), row.status());
            }
        }

        Map<Long, CustomerProfileSnapshot> customersById = customerIds.isEmpty()
                ? Collections.emptyMap()
                : customerLookupService.findProfilesByCustomerIds(customerIds);

        StaffOrderListResponse response = orderMapper.toStaffOrderListResponse(
                orderPage,
                itemCountsByOrderId,
                primaryProductIdByOrderId,
                productsById,
                deliveriesByOrderId,
                paymentStatusesByOrderId,
                customersById
        );

        log.info("Staff fulfillment queue retrieved [employeeAccountId={}] [page={}] [size={}] [totalElements={}]",
                employeeAccountId,
                response.meta().getPage(),
                response.meta().getSize(),
                response.meta().getTotalElements());

        return response;
    }
}
