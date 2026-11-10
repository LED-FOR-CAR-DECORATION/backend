package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViewCartUseCase {

    private static final Logger log = LoggerFactory.getLogger(ViewCartUseCase.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductLookupService productLookupService;
    private final CartItemAvailabilityService cartItemAvailabilityService;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse execute(Long customerId) {
        Optional<CartEntity> existingCart = cartRepository.findByCustomerId(customerId);
        boolean initialized = existingCart.isEmpty();

        CartEntity cart = existingCart.orElseGet(() -> cartRepository.save(CartEntity.create(customerId)));

        List<CartItemEntity> items = initialized
                ? Collections.emptyList()
                : cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(cart.getCartId());

        Map<Long, ProductSnapshot> productsById = items.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(
                        items.stream().map(CartItemEntity::getProductId).collect(Collectors.toSet()));

        Map<Long, AvailabilityCheckResult> availabilityByItemId = items.stream()
                .collect(Collectors.toMap(
                        CartItemEntity::getCartItemId,
                        item -> cartItemAvailabilityService.evaluate(
                                customerId,
                                productsById.get(item.getProductId()),
                                item.getQuantity()
                        )
                ));

        log.info("Shopping cart viewed [customerId={}] [cartId={}] [itemCount={}] [initialized={}]",
                customerId, cart.getCartId(), items.size(), initialized);

        return cartMapper.toCartResponse(cart, items, productsById, availabilityByItemId);
    }
}
