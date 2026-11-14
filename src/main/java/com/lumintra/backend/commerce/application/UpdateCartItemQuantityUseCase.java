package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.UpdateCartItemQuantityRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.shared.exception.commerce.CartItemNotFoundException;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UpdateCartItemQuantityUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateCartItemQuantityUseCase.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductLookupService productLookupService;
    private final ProductAvailabilityPort productAvailabilityPort;
    private final CartItemAvailabilityService cartItemAvailabilityService;
    private final CartMapper cartMapper;

    @Transactional
    public CartResponse execute(Long customerId, Long itemId, UpdateCartItemQuantityRequest request) {
        CartItemEntity cartItem = cartItemRepository.findByCartItemIdAndCart_CustomerId(itemId, customerId)
                .orElseThrow(() -> {
                    log.warn("Cart item not found or access denied [itemId={}] [customerId={}]", itemId, customerId);
                    return new CartItemNotFoundException("Cart item not found.");
                });

        ProductSnapshot product = productLookupService.findSnapshotById(cartItem.getProductId())
                .orElseThrow(() -> new ProductNotAvailableException("Product not found or unavailable for purchase."));

        if (product.status() != ProductStatus.PUBLISHED) {
            throw new ProductNotAvailableException("Product is not published for purchase.");
        }

        int requestedQuantity = request.quantity();
        validateAndAuthorizeQuantity(customerId, product, requestedQuantity);

        cartItem.updateQuantityAndPrice(requestedQuantity, product.basePrice());
        cartItemRepository.save(cartItem);

        CartEntity cart = cartItem.getCart();
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

        log.info("Cart item quantity updated [customerId={}] [cartId={}] [cartItemId={}] [productId={}] [quantity={}]",
                customerId, savedCart.getCartId(), cartItem.getCartItemId(), product.productId(), requestedQuantity);

        return cartMapper.toCartResponse(savedCart, allItems, productsById, availabilityByItemId);
    }

    private void validateAndAuthorizeQuantity(Long customerId, ProductSnapshot product, int requestedQuantity) {
        ProductType productType = product.productType();

        if (productType == ProductType.CHARACTER || productType == ProductType.SUBSCRIPTION) {
            if (productAvailabilityPort.isDigitalProductOwned(customerId, product.productId(), productType)) {
                throw new ProductAlreadyOwnedException("Customer already owns this digital product.");
            }
            throw new DigitalItemQuantityLimitException("Quantity cannot be modified for digital products in the cart.");
        }

        if (productType == ProductType.LED) {
            if (!productAvailabilityPort.hasSufficientLedStock(product.productId(), requestedQuantity)) {
                throw new InsufficientStockException("Requested LED quantity exceeds available stock.");
            }
            return;
        }

        if (productType == ProductType.BUNDLE) {
            AvailabilityCheckResult availability =
                    cartItemAvailabilityService.evaluate(customerId, product, requestedQuantity);
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
            return;
        }

        throw new ProductNotAvailableException("Unsupported product type.");
    }
}
