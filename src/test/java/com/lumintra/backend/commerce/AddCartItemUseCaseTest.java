package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.AddCartItemRequest;
import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.AddCartItemUseCase;
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
class AddCartItemUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private ProductAvailabilityPort productAvailabilityPort;

    private AddCartItemUseCase addCartItemUseCase;

    @BeforeEach
    void setUp() {
        CartItemAvailabilityService availabilityService = new CartItemAvailabilityService(productAvailabilityPort);
        CartMapper cartMapper = new CartMapper();
        addCartItemUseCase = new AddCartItemUseCase(
                cartRepository,
                cartItemRepository,
                productLookupService,
                productAvailabilityPort,
                availabilityService,
                cartMapper
        );
    }

    @Test
    @DisplayName("UTCID05 [N]: AC01 & AC05 auto-initializes cart and adds new LED item within stock")
    void execute_newCartAndLedProductWithinStock_createsCartAndAddsItem() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        ProductSnapshot led = new ProductSnapshot(
                12L, "LED Matrix 32x32", ProductType.LED, "https://img.lumintra.shop/m32.png",
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );
        CartEntity persistedCart = CartEntity.of(101L, customerId, now);
        CartItemEntity savedItem = CartItemEntity.of(501L, persistedCart, 12L, 2, new BigDecimal("350000.00"), now);

        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(led));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenReturn(persistedCart);
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 12L)).thenReturn(Optional.empty());
        when(productAvailabilityPort.hasSufficientLedStock(12L, 2)).thenReturn(true);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(savedItem));
        when(productLookupService.findSnapshotsByIds(Set.of(12L))).thenReturn(Map.of(12L, led));

        CartResponse response = addCartItemUseCase.execute(customerId, new AddCartItemRequest(12L, 2));

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("700000.00");
        assertThat(response.items().get(0).isAvailable()).isTrue();
        verify(cartItemRepository).save(any(CartItemEntity.class));
    }

    @Test
    @DisplayName("UTCID06 [N]: AC05 accumulates quantity when adding an LED product already in cart")
    void execute_existingLedInCart_accumulatesQuantityAndChecksCombinedStock() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        ProductSnapshot led = new ProductSnapshot(
                12L, "LED Matrix 32x32", ProductType.LED, null,
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity existingItem = CartItemEntity.of(501L, cart, 12L, 2, new BigDecimal("320000.00"), now);

        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(led));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 12L)).thenReturn(Optional.of(existingItem));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 5)).thenReturn(true);
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(existingItem));
        when(productLookupService.findSnapshotsByIds(Set.of(12L))).thenReturn(Map.of(12L, led));

        CartResponse response = addCartItemUseCase.execute(customerId, new AddCartItemRequest(12L, 3));

        assertThat(existingItem.getQuantity()).isEqualTo(5);
        assertThat(existingItem.getUnitPrice()).isEqualByComparingTo("350000.00");
        assertThat(response.items().get(0).quantity()).isEqualTo(5);
        assertThat(response.items().get(0).subtotal()).isEqualByComparingTo("1750000.00");
        verify(cartItemRepository).save(existingItem);
    }

    @Test
    @DisplayName("UTCID07 [B]: AC05 throws InsufficientStockException when accumulated LED quantity exceeds stock")
    void execute_ledQuantityExceedsStock_throwsInsufficientStockException() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        ProductSnapshot led = new ProductSnapshot(
                12L, "LED Matrix 32x32", ProductType.LED, null,
                new BigDecimal("350000.00"), ProductStatus.PUBLISHED
        );
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity existingItem = CartItemEntity.of(501L, cart, 12L, 4, new BigDecimal("350000.00"), now);

        when(productLookupService.findSnapshotById(12L)).thenReturn(Optional.of(led));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 12L)).thenReturn(Optional.of(existingItem));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 6)).thenReturn(false);

        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(12L, 2)))
                .isInstanceOf(InsufficientStockException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID08 [A]: AC03 throws ProductNotAvailableException when product does not exist or is UNPUBLISHED")
    void execute_missingOrUnpublishedProduct_throwsProductNotAvailableException() {
        Long customerId = 42L;
        ProductSnapshot unpublished = new ProductSnapshot(
                15L, "Draft Character", ProductType.CHARACTER, null,
                new BigDecimal("59000.00"), ProductStatus.UNPUBLISHED
        );

        when(productLookupService.findSnapshotById(999L)).thenReturn(Optional.empty());
        when(productLookupService.findSnapshotById(15L)).thenReturn(Optional.of(unpublished));

        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(999L, 1)))
                .isInstanceOf(ProductNotAvailableException.class);

        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(15L, 1)))
                .isInstanceOf(ProductNotAvailableException.class);

        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    @DisplayName("UTCID09 [A]: AC04 rejects CHARACTER/SUBSCRIPTION when quantity > 1, already in cart, or already owned")
    void execute_digitalProductConstraints_throwsExpectedExceptions() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        ProductSnapshot character = new ProductSnapshot(
                30L, "Cyber Mascot", ProductType.CHARACTER, null,
                new BigDecimal("59000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot subscription = new ProductSnapshot(
                31L, "Lumintra Plus", ProductType.SUBSCRIPTION, null,
                new BigDecimal("99000.00"), ProductStatus.PUBLISHED
        );
        CartEntity cart = CartEntity.of(101L, customerId, now);

        when(productLookupService.findSnapshotById(30L)).thenReturn(Optional.of(character));
        when(productLookupService.findSnapshotById(31L)).thenReturn(Optional.of(subscription));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));

        // Case 1: Already owned character
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 30L)).thenReturn(Optional.empty());
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(true);
        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(30L, 1)))
                .isInstanceOf(ProductAlreadyOwnedException.class);

        // Case 2: Unowned character with quantity > 1
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(false);
        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(30L, 2)))
                .isInstanceOf(DigitalItemQuantityLimitException.class);

        // Case 3: Unowned subscription already present in cart
        CartItemEntity existingSub = CartItemEntity.of(502L, cart, 31L, 1, new BigDecimal("99000.00"), now);
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 31L)).thenReturn(Optional.of(existingSub));
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 31L, ProductType.SUBSCRIPTION)).thenReturn(false);
        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(31L, 1)))
                .isInstanceOf(DigitalItemQuantityLimitException.class);

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID10 [N/A]: AC05 adds valid BUNDLE and rejects BUNDLE when stock insufficient or character owned")
    void execute_bundleProduct_validatesBundleAvailabilityAndPersists() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        ProductSnapshot bundle = new ProductSnapshot(
                70L, "Starter Creator Bundle", ProductType.BUNDLE, null,
                new BigDecimal("499000.00"), ProductStatus.PUBLISHED
        );
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity savedBundleItem = CartItemEntity.of(505L, cart, 70L, 1, new BigDecimal("499000.00"), now);

        when(productLookupService.findSnapshotById(70L)).thenReturn(Optional.of(bundle));
        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_CartIdAndProductId(101L, 70L)).thenReturn(Optional.empty());

        // 1. Out of stock LED inside bundle
        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 1))
                .thenReturn(AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_OUT_OF_STOCK));
        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(70L, 1)))
                .isInstanceOf(InsufficientStockException.class);

        // 2. Character inside bundle already owned
        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 1))
                .thenReturn(AvailabilityCheckResult.unavailable(AvailabilityCheckResult.REASON_ALREADY_OWNED));
        assertThatThrownBy(() -> addCartItemUseCase.execute(customerId, new AddCartItemRequest(70L, 1)))
                .isInstanceOf(ProductAlreadyOwnedException.class);

        // 3. Valid bundle
        when(productAvailabilityPort.evaluateBundleAvailability(customerId, 70L, 1))
                .thenReturn(AvailabilityCheckResult.availableResult());
        when(cartRepository.save(cart)).thenReturn(cart);
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(savedBundleItem));
        when(productLookupService.findSnapshotsByIds(Set.of(70L))).thenReturn(Map.of(70L, bundle));

        CartResponse response = addCartItemUseCase.execute(customerId, new AddCartItemRequest(70L, 1));
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productId()).isEqualTo(70L);
        assertThat(response.items().get(0).isAvailable()).isTrue();
    }
}
