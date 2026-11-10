package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.OrderFilterRequest;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemSummaryRow;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderSpecifications;
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

@Service
@RequiredArgsConstructor
public class ListOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListOrdersUseCase.class);
    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("orderId")
    );

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductLookupService productLookupService;
    private final OrderMapper orderMapper;

    @Transactional(readOnly = true)
    public OrderListResponse execute(Long customerId, OrderFilterRequest request) {
        OrderFilterRequest effectiveRequest = request != null ? request : new OrderFilterRequest();
        Pageable pageable = PageRequest.of(effectiveRequest.page(), effectiveRequest.size(), DEFAULT_SORT);

        Page<OrderEntity> orderPage = orderRepository.findAll(
                OrderSpecifications.withCustomerFilter(customerId, effectiveRequest),
                pageable
        );

        if (orderPage.isEmpty()) {
            OrderListResponse emptyResponse = orderMapper.toOrderListResponse(
                    orderPage,
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
            log.info("Order history retrieved [customerId={}] [page={}] [size={}] [totalElements={}]",
                    customerId, emptyResponse.meta().getPage(), emptyResponse.meta().getSize(), emptyResponse.meta().getTotalElements());
            return emptyResponse;
        }

        List<Long> orderIds = orderPage.getContent().stream()
                .map(OrderEntity::getOrderId)
                .toList();

        List<OrderItemSummaryRow> itemRows = orderItemRepository.findSummaryRowsByOrderIds(orderIds);

        Map<Long, Integer> itemCountsByOrderId = new HashMap<>();
        Map<Long, Long> primaryProductIdByOrderId = new HashMap<>();
        Set<Long> primaryProductIds = new LinkedHashSet<>();

        for (OrderItemSummaryRow row : itemRows) {
            itemCountsByOrderId.merge(row.orderId(), 1, Integer::sum);
            if (primaryProductIdByOrderId.putIfAbsent(row.orderId(), row.productId()) == null && row.productId() != null) {
                primaryProductIds.add(row.productId());
            }
        }

        Map<Long, ProductSnapshot> productsById = primaryProductIds.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(primaryProductIds);

        OrderListResponse response = orderMapper.toOrderListResponse(
                orderPage,
                itemCountsByOrderId,
                primaryProductIdByOrderId,
                productsById
        );

        log.info("Order history retrieved [customerId={}] [page={}] [size={}] [totalElements={}]",
                customerId, response.meta().getPage(), response.meta().getSize(), response.meta().getTotalElements());

        return response;
    }
}
