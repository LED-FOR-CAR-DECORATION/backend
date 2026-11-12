package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.UpdateCartItemQuantityRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.UpdateCartItemQuantityUseCase;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateCartItemQuantityUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private ProductAvailabilityPort productAvailabilityPort;

    private UpdateCartItemQuantityUseCase updateCartItemQuantityUseCase;

    @BeforeEach
    void setUp() {
        CartItemAvailabilityService availabilityService = new CartItemAvailabilityService(productAvailabilityPort);
        CartMapper cartMapper = new CartMapper();
        updateCartItemQuantityUseCase = new UpdateCartItemQuantityUseCase(
                cartRepository,
                cartItemRepository,
                productLookupService,
                productAvailabilityPort,
                availabilityService,
                cartMapper
        );
    }

    @Test
    @DisplayName("UTCID01 [N]: AC04 & AC06 overwrites LED quantity, syncs current basePrice, touches cart, and returns refreshed CartResponse")
    void execute_ledOverwriteQuantity_syncsPriceAndReturnsRefreshedCart() {
        Long customerId = 42L;
        Instant addedAt = Instant.parse("2026-11-10T02:00:00Z");
        Instant oldUpdatedAt = Instant.parse("2026-11-10T03:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, oldUpdatedAt);
        CartItemEntity ledItem = CartItemEntity.of(501L, cart, 12L, 2, new BigDecimal("320000.00"), addedAt);
        ProductSnapshot ledSnapshot = new ProductSnapshot(
                12L, "LED Matrix Panel 32x32", ProductType.LED, "https://img.lumintra.shop/matrix32.png",
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(ledItem));
        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(ledSnapshot));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 4)).thenReturn(true);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(ledItem));
        when(productLookupService.findSnapshotsByIds(Set.of(12L))).thenReturn(Map.of(12L, ledSnapshot));

        CartResponse response = updateCartItemQuantityUseCase.execute(
                customerId, 501L, new UpdateCartItemQuantityRequest(4)
        );

        assertThat(ledItem.getQuantity()).isEqualTo(4);
        assertThat(ledItem.getUnitPrice()).isEqualByComparingTo("350000.00");
        assertThat(cart.getUpdatedAt()).isAfterOrEqualTo(oldUpdatedAt);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).cartItemId()).isEqualTo(501L);
        assertThat(response.items().get(0).quantity()).isEqualTo(4);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("350000.00");
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("1400000.00");
        assertThat(response.items().get(0).isAvailable()).isTrue();
        assertThat(response.items().get(0).unavailableReason()).isNull();

        verify(productAvailabilityPort, times(2)).hasSufficientLedStock(12L, 4);
        verify(cartItemRepository).save(ledItem);
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("UTCID02 [B]: AC04 permits decreasing LED quantity to exact available stock even when prior quantity exceeded stock")
    void execute_ledDecreaseToExactAvailableStock_succeeds() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");

        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(501L, cart, 12L, 5, new BigDecimal("350000.00"), now);
        ProductSnapshot ledSnapshot = new ProductSnapshot(
                12L, "LED Matrix Panel 32x32", ProductType.LED, null,
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(ledItem));
        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(ledSnapshot));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 4)).thenReturn(true);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(ledItem));
        when(productLookupService.findSnapshotsByIds(Set.of(12L))).thenReturn(Map.of(12L, ledSnapshot));

        CartResponse response = updateCartItemQuantityUseCase.execute(
                customerId, 501L, new UpdateCartItemQuantityRequest(4)
        );

        assertThat(ledItem.getQuantity()).isEqualTo(4);
        assertThat(response.items().get(0).quantity()).isEqualTo(4);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("1400000.00");
        verify(cartItemRepository).save(ledItem);
    }

    @Test
    @DisplayName("UTCID03 [A]: AC02 throws CartItemNotFoundException when itemId is missing or unowned without creating cart")
    void execute_missingOrUnownedCartItem_throwsCartItemNotFoundWithoutCreatingCart() {
        Long customerId = 42L;
        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(999L, customerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 999L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(CartItemNotFoundException.class);

        verifyNoInteractions(cartRepository, productLookupService, productAvailabilityPort);
    }

    @Test
    @DisplayName("UTCID04 [A]: AC04 & AC05 throw ProductNotAvailableException when product is missing or not PUBLISHED before stock/ownership checks")
    void execute_missingOrNonPublishedProduct_throwsProductNotAvailableBeforeStockCheck() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);

        CartItemEntity deletedItem = CartItemEntity.of(501L, cart, 99L, 2, new BigDecimal("350000.00"), now);
        CartItemEntity archivedItem = CartItemEntity.of(502L, cart, 15L, 2, new BigDecimal("350000.00"), now);
        ProductSnapshot archivedLed = new ProductSnapshot(
                15L, "Legacy LED Panel", ProductType.LED, null,
                new BigDecimal("350000.00"), ProductStatus.ARCHIVED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(deletedItem));
        when(productLookupService.findSnapshotById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 501L, new UpdateCartItemQuantityRequest(1)
        )).isInstanceOf(ProductNotAvailableException.class);

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(502L, customerId)).thenReturn(Optional.of(archivedItem));
        when(productLookupService.findSnapshotById(15L)).thenReturn(Optional.of(archivedLed));

        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 502L, new UpdateCartItemQuantityRequest(1)
        )).isInstanceOf(ProductNotAvailableException.class);

        verifyNoInteractions(productAvailabilityPort, cartRepository);
        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: AC05 checks ALREADY_OWNED first and strictly rejects any PUT on unowned CHARACTER/SUBSCRIPTION (qty=1 or qty>1)")
    void execute_digitalItems_enforceOwnedPriorityAndStrictPutRejection() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);

        CartItemEntity characterItem = CartItemEntity.of(503L, cart, 30L, 1, new BigDecimal("59000.00"), now);
        CartItemEntity subscriptionItem = CartItemEntity.of(504L, cart, 31L, 1, new BigDecimal("99000.00"), now);

        ProductSnapshot character = new ProductSnapshot(
                30L, "Cyber Mascot", ProductType.CHARACTER, null,
                new BigDecimal("59000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot subscription = new ProductSnapshot(
                31L, "Lumintra All-Access VIP Pass", ProductType.SUBSCRIPTION, null,
                new BigDecimal("99000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(503L, customerId)).thenReturn(Optional.of(characterItem));
        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(504L, customerId)).thenReturn(Optional.of(subscriptionItem));
        when(productLookupService.findSnapshotById(30L)).thenReturn(Optional.of(character));
        when(productLookupService.findSnapshotById(31L)).thenReturn(Optional.of(subscription));

        // 1. Owned digital item rejects with PRODUCT_ALREADY_OWNED before DIGITAL_ITEM_QUANTITY_LIMIT
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(true);
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 503L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(ProductAlreadyOwnedException.class);

        // 2. Unowned CHARACTER with quantity = 1 is strictly rejected with DIGITAL_ITEM_QUANTITY_LIMIT
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(false);
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 503L, new UpdateCartItemQuantityRequest(1)
        )).isInstanceOf(DigitalItemQuantityLimitException.class);

        // 3. Unowned SUBSCRIPTION with quantity = 2 is strictly rejected with DIGITAL_ITEM_QUANTITY_LIMIT
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 31L, ProductType.SUBSCRIPTION)).thenReturn(false);
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 504L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(DigitalItemQuantityLimitException.class);

        verify(cartItemRepository, never()).save(any());
        verifyNoInteractions(cartRepository);
    }

    @Test
    @DisplayName("UTCID06 [B]: AC04 throws InsufficientStockException when requested LED quantity exceeds real-time stock")
    void execute_ledQuantityExceedsStock_throwsInsufficientStockException() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity ledItem = CartItemEntity.of(501L, cart, 12L, 5, new BigDecimal("350000.00"), now);
        ProductSnapshot ledSnapshot = new ProductSnapshot(
                12L, "LED Matrix Panel 32x32", ProductType.LED, null,
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(501L, customerId)).thenReturn(Optional.of(ledItem));
        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(ledSnapshot));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 4)).thenReturn(false);

        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 501L, new UpdateCartItemQuantityRequest(4)
        )).isInstanceOf(InsufficientStockException.class);

        assertThat(ledItem.getQuantity()).isEqualTo(5);
        verify(cartItemRepository, never()).save(any());
        verifyNoInteractions(cartRepository);
    }

    @Test
    @DisplayName("UTCID07 [N]: AC04 & AC06 updates valid BUNDLE quantity with overwrite semantics and returns all cart items")
    void execute_validBundleQuantityUpdate_persistsAndReturnsFullCart() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity bundleItem = CartItemEntity.of(505L, cart, 70L, 1, new BigDecimal("499000.00"), now);
        ProductSnapshot bundle = new ProductSnapshot(
                70L, "Starter Creator Bundle", ProductType.BUNDLE, "https://img.lumintra.shop/bundle.png",
                new BigDecimal("499000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(505L, customerId)).thenReturn(Optional.of(bundleItem));
        when(productLookupService.findSnapshotById(70L)).thenReturn(Optional.of(bundle));
        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 2))
                .thenReturn(AvailabilityCheckResult.availableResult());
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(bundleItem));
        when(productLookupService.findSnapshotsByIds(Set.of(70L))).thenReturn(Map.of(70L, bundle));

        CartResponse response = updateCartItemQuantityUseCase.execute(
                customerId, 505L, new UpdateCartItemQuantityRequest(2)
        );

        assertThat(bundleItem.getQuantity()).isEqualTo(2);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("998000.00");
        verify(cartItemRepository).save(bundleItem);
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("UTCID08 [A]: AC04 rejects BUNDLE update when character owned, hardware out of stock, or bundle outside active window")
    void execute_bundleUnavailable_throwsMappedCommerceExceptions() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity bundleItem = CartItemEntity.of(505L, cart, 70L, 1, new BigDecimal("499000.00"), now);
        ProductSnapshot bundle = new ProductSnapshot(
                70L, "Starter Creator Bundle", ProductType.BUNDLE, null,
                new BigDecimal("499000.00"), ProductStatus.PUBLISHED
        );

        when(cartItemRepository.findByCartItemIdAndCart_CustomerId(505L, customerId)).thenReturn(Optional.of(bundleItem));
        when(productLookupService.findSnapshotById(70L)).thenReturn(Optional.of(bundle));

        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 2))
                .thenReturn(AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_ALREADY_OWNED));
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 505L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(ProductAlreadyOwnedException.class);

        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 2))
                .thenReturn(AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_OUT_OF_STOCK));
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 505L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(InsufficientStockException.class);

        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 2))
                .thenReturn(AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE));
        assertThatThrownBy(() -> updateCartItemQuantityUseCase.execute(
                customerId, 505L, new UpdateCartItemQuantityRequest(2)
        )).isInstanceOf(ProductNotAvailableException.class);

        verify(cartItemRepository, never()).save(any());
        verifyNoInteractions(cartRepository);
    }
}
