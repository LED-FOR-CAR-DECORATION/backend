package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.AddCartItemRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.shared.exception.commerce.DigitalItemQuantityLimitException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
import com.lumintra.backend.shared.exception.commerce.ProductNotAvailableException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AddCartItemUseCase {

    private static final Logger log = LoggerFactory.getLogger(AddCartItemUseCase.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductLookupService productLookupService;
    private final ProductAvailabilityPort productAvailabilityPort;
    private final CartItemAvailabilityService cartItemAvailabilityService;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse execute(Long customerId, AddCartItemRequest request) {
        ProductSnapshot product = productLookupService.findSnapshotById(request.productId())
                .orElseThrow(() -> new ProductNotAvailableException("Product not found or unavailable for purchase."));

        if (product.status() != ProductStatus.PUBLISHED) {
            throw new ProductNotAvailableException("Product is not published for purchase.");
        }

        CartEntity cart = cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(CartEntity.create(customerId)));

        Optional<CartItemEntity> existingItemOpt =
                cartItemRepository.findByCart_CartIdAndProductId(cart.getCartId(), product.productId());

        if (product.productType() == ProductType.CHARACTER || product.productType() == ProductType.SUBSCRIPTION) {
            handleDigitalProduct(customerId, cart, product, request.quantity(), existingItemOpt);
        } else if (product.productType() == ProductType.LED) {
            handleLedProduct(cart, product, request.quantity(), existingItemOpt);
        } else if (product.productType() == ProductType.BUNDLE) {
            handleBundleProduct(customerId, cart, product, request.quantity(), existingItemOpt);
        } else {
            throw new ProductNotAvailableException("Unsupported product type.");
        }

        cart.touch();
        CartEntity savedCart = cartRepository.save(cart);

        List<CartItemEntity> allItems = cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(savedCart.getCartId());
        Map<Long, ProductSnapshot> productsById = productLookupService.findSnapshotsByIds(
                allItems.stream().map(CartItemEntity::getProductId).collect(Collectors.toSet()));

        Map<Long, AvailabilityCheckResult> availabilityByItemId = allItems.stream()
                .collect(Collectors.toMap(
                        CartItemEntity::getCartItemId,
                        item -> cartItemAvailabilityService.evaluate(
                                customerId,
                                productsById.get(item.getProductId()),
                                item.getQuantity()
                        )
                ));

        log.info("Cart item added [customerId={}] [cartId={}] [productId={}] [requestedQuantity={}]",
                customerId, savedCart.getCartId(), product.productId(), request.quantity());

        return cartMapper.toCartResponse(savedCart, allItems, productsById, availabilityByItemId);
    }

    private void handleDigitalProduct(
            Long customerId,
            CartEntity cart,
            ProductSnapshot product,
            int requestedQuantity,
            Optional<CartItemEntity> existingItemOpt) {
        if (productAvailabilityPort.isDigitalProductOwned(customerId, product.productId(), product.productType())) {
            throw new ProductAlreadyOwnedException("Customer already owns this digital product.");
        }

        if (requestedQuantity > 1 || existingItemOpt.isPresent()) {
            throw new DigitalItemQuantityLimitException("Digital products cannot exceed a quantity of 1 in the cart.");
        }

        cartItemRepository.save(CartItemEntity.create(cart, product.productId(), 1, product.basePrice()));
    }

    private void handleLedProduct(
            CartEntity cart,
            ProductSnapshot product,
            int requestedQuantity,
            Optional<CartItemEntity> existingItemOpt) {
        int newQuantity = existingItemOpt.map(CartItemEntity::getQuantity).orElse(0) + requestedQuantity;
        if (!productAvailabilityPort.hasSufficientLedStock(product.productId(), newQuantity)) {
            throw new InsufficientStockException("Requested LED quantity exceeds available stock.");
        }

        upsertCartItem(cart, product, newQuantity, existingItemOpt);
    }

    private void handleBundleProduct(
            Long customerId,
            CartEntity cart,
            ProductSnapshot product,
            int requestedQuantity,
            Optional<CartItemEntity> existingItemOpt) {
        int newQuantity = existingItemOpt.map(CartItemEntity::getQuantity).orElse(0) + requestedQuantity;
        AvailabilityCheckResult availability = cartItemAvailabilityService.evaluate(customerId, product, newQuantity);

        if (!availability.available()) {
            String reason = availability.unavailableReason();
            if (AvailabilityCheckResult.REASON_ALREADY_OWNED.equals(reason)) {
                throw new ProductAlreadyOwnedException("Customer already owns a character included in this bundle.");
            }
            if (AvailabilityCheckResult.REASON_OUT_OF_STOCK.equals(reason)) {
                throw new InsufficientStockException("Requested bundle quantity exceeds available hardware stock.");
            }
            throw new ProductNotAvailableException("Bundle is unavailable or outside its active window.");
        }

        upsertCartItem(cart, product, newQuantity, existingItemOpt);
    }

    private void upsertCartItem(
            CartEntity cart,
            ProductSnapshot product,
            int newQuantity,
            Optional<CartItemEntity> existingItemOpt) {
        if (existingItemOpt.isPresent()) {
            CartItemEntity existing = existingItemOpt.get();
            existing.updateQuantityAndPrice(newQuantity, product.basePrice());
            cartItemRepository.save(existing);
        } else {
            cartItemRepository.save(CartItemEntity.create(cart, product.productId(), newQuantity, product.basePrice()));
        }
    }
}
