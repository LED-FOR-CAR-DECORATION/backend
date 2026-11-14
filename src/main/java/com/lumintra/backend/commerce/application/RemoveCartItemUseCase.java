package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.shared.exception.commerce.CartItemNotFoundException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RemoveCartItemUseCase {

    private static final Logger log = LoggerFactory.getLogger(RemoveCartItemUseCase.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductLookupService productLookupService;
    private final CartItemAvailabilityService cartItemAvailabilityService;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse execute(Long customerId, Long itemId) {
        CartItemEntity cartItem = cartItemRepository.findByCartItemIdAndCart_CustomerId(itemId, customerId)
                .orElseThrow(() -> {
                    log.warn("Cart item not found or access denied [itemId={}] [customerId={}]", itemId, customerId);
                    return new CartItemNotFoundException("Cart item not found.");
                });

        CartEntity cart = cartItem.getCart();
        Long removedProductId = cartItem.getProductId();

        cartItemRepository.delete(cartItem);
        cartItemRepository.flush();

        cart.touch();
        CartEntity savedCart = cartRepository.save(cart);

        List<CartItemEntity> remainingItems =
                cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(savedCart.getCartId());

        Map<Long, ProductSnapshot> productsById = remainingItems.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(
                        remainingItems.stream().map(CartItemEntity::getProductId).collect(Collectors.toSet()));

        Map<Long, AvailabilityCheckResult> availabilityByItemId = remainingItems.stream()
                .collect(Collectors.toMap(
                        CartItemEntity::getCartItemId,
                        item -> cartItemAvailabilityService.evaluate(
                                customerId,
                                productsById.get(item.getProductId()),
                                item.getQuantity()
                        )
                ));

        log.info("Cart item removed [customerId={}] [cartId={}] [cartItemId={}] [productId={}] [remainingCount={}]",
                customerId, savedCart.getCartId(), itemId, removedProductId, remainingItems.size());

        return cartMapper.toCartResponse(savedCart, remainingItems, productsById, availabilityByItemId);
    }
}
