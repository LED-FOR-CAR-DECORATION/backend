package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.response.CartResponse;
import com.lumintra.backend.commerce.application.ViewCartUseCase;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.service.CartItemAvailabilityService;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.CartMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductLookupService productLookupService;

    @Mock
    private ProductAvailabilityPort productAvailabilityPort;

    private ViewCartUseCase viewCartUseCase;

    @BeforeEach
    void setUp() {
        CartItemAvailabilityService availabilityService = new CartItemAvailabilityService(productAvailabilityPort);
        CartMapper cartMapper = new CartMapper();
        viewCartUseCase = new ViewCartUseCase(
                cartRepository,
                cartItemRepository,
                productLookupService,
                availabilityService,
                cartMapper
        );
    }

    @Test
    @DisplayName("UTCID05 [N]: AC01 initializes and persists empty cart when customer has no active cart")
    void execute_noActiveCart_initializesEmptyCart() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity savedCart = CartEntity.of(101L, customerId, now);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
        when(cartRepository.save(any(CartEntity.class))).thenReturn(savedCart);

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        assertThat(response.updatedAt()).isEqualTo(now);

        verify(cartRepository).save(any(CartEntity.class));
        verifyNoInteractions(cartItemRepository, productLookupService, productAvailabilityPort);
    }

    @Test
    @DisplayName("UTCID06 [N]: AC02 computes unitPrice and subtotal from current PRODUCT.base_price instead of stale CART_ITEM.unit_price")
    void execute_existingCartWithPriceChange_usesCurrentProductBasePrice() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        // Stored unit_price in cart_item was 250000.00, while current product base_price increased to 300000.00
        CartItemEntity item = CartItemEntity.of(501L, cart, 12L, 3, new BigDecimal("250000.00"), now);

        ProductSnapshot snapshot = new ProductSnapshot(
                12L, "LED Strip RGB", ProductType.LED, "https://img.lumintra.shop/strip.png",
                new BigDecimal("300000.00"), ProductStatus.PUBLISHED
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of(item));
        when(productLookupService.findSnapshotsByIds(Set.of(12L))).thenReturn(Map.of(12L, snapshot));
        when(productAvailabilityPort.hasSufficientLedStock(12L, 3)).thenReturn(true);

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.items()).hasSize(1);
        CartResponse.CartItemResponse itemDto = response.items().get(0);
        assertThat(itemDto.unitPrice()).isEqualByComparingTo("300000.00");
        assertThat(itemDto.subtotal()).isEqualByComparingTo("900000.00");
        assertThat(itemDto.isAvailable()).isTrue();
        assertThat(itemDto.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("UTCID07 [A]: AC03 flags UNPUBLISHED and ARCHIVED products with PRODUCT_UNAVAILABLE and PRODUCT_ARCHIVED")
    void execute_unpublishedAndArchivedProducts_flaggedUnavailableWithExactReasons() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity unpublishedItem = CartItemEntity.of(501L, cart, 10L, 1, new BigDecimal("100000.00"), now);
        CartItemEntity archivedItem = CartItemEntity.of(502L, cart, 11L, 1, new BigDecimal("200000.00"), now);

        ProductSnapshot unpublishedProduct = new ProductSnapshot(
                10L, "Draft Pixel Art", ProductType.CHARACTER, null, new BigDecimal("100000.00"), ProductStatus.UNPUBLISHED
        );
        ProductSnapshot archivedProduct = new ProductSnapshot(
                11L, "Legacy LED Box", ProductType.LED, null, new BigDecimal("200000.00"), ProductStatus.ARCHIVED
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L))
                .thenReturn(List.of(unpublishedItem, archivedItem));
        when(productLookupService.findSnapshotsByIds(Set.of(10L, 11L)))
                .thenReturn(Map.of(10L, unpublishedProduct, 11L, archivedProduct));

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).isAvailable()).isFalse();
        assertThat(response.items().get(0).unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_PRODUCT_UNAVAILABLE);
        assertThat(response.items().get(1).isAvailable()).isFalse();
        assertThat(response.items().get(1).unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_PRODUCT_ARCHIVED);
    }

    @Test
    @DisplayName("UTCID08 [B]: AC03 LED stock boundary marks OUT_OF_STOCK when stock < quantity and available when stock >= quantity")
    void execute_ledStockBoundary_flagsOutOfStockOnlyWhenInsufficient() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity outOfStockItem = CartItemEntity.of(501L, cart, 20L, 5, new BigDecimal("400000.00"), now);
        CartItemEntity exactStockItem = CartItemEntity.of(502L, cart, 21L, 2, new BigDecimal("450000.00"), now);

        ProductSnapshot ledOut = new ProductSnapshot(
                20L, "LED Matrix Pro", ProductType.LED, null, new BigDecimal("400000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot ledOk = new ProductSnapshot(
                21L, "LED Matrix Lite", ProductType.LED, null, new BigDecimal("450000.00"), ProductStatus.PUBLISHED
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L))
                .thenReturn(List.of(outOfStockItem, exactStockItem));
        when(productLookupService.findSnapshotsByIds(Set.of(20L, 21L)))
                .thenReturn(Map.of(20L, ledOut, 21L, ledOk));
        when(productAvailabilityPort.hasSufficientLedStock(20L, 5)).thenReturn(false);
        when(productAvailabilityPort.hasSufficientLedStock(21L, 2)).thenReturn(true);

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.items().get(0).isAvailable()).isFalse();
        assertThat(response.items().get(0).unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_OUT_OF_STOCK);
        assertThat(response.items().get(1).isAvailable()).isTrue();
        assertThat(response.items().get(1).unavailableReason()).isNull();
    }

    @Test
    @DisplayName("UTCID09 [A]: AC03 flags CHARACTER and SUBSCRIPTION items as ALREADY_OWNED when customer owns them")
    void execute_ownedDigitalProducts_flaggedAlreadyOwned() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);
        CartItemEntity characterItem = CartItemEntity.of(501L, cart, 30L, 1, new BigDecimal("59000.00"), now);
        CartItemEntity subscriptionItem = CartItemEntity.of(502L, cart, 31L, 1, new BigDecimal("99000.00"), now);

        ProductSnapshot characterProduct = new ProductSnapshot(
                30L, "Cyber Mascot", ProductType.CHARACTER, null, new BigDecimal("59000.00"), ProductStatus.PUBLISHED
        );
        ProductSnapshot subscriptionProduct = new ProductSnapshot(
                31L, "Lumintra Plus Monthly", ProductType.SUBSCRIPTION, null, new BigDecimal("99000.00"), ProductStatus.PUBLISHED
        );

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L))
                .thenReturn(List.of(characterItem, subscriptionItem));
        when(productLookupService.findSnapshotsByIds(Set.of(30L, 31L)))
                .thenReturn(Map.of(30L, characterProduct, 31L, subscriptionProduct));
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 30L, ProductType.CHARACTER)).thenReturn(true);
        when(productAvailabilityPort.isDigitalProductOwned(customerId, 31L, ProductType.SUBSCRIPTION)).thenReturn(true);

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.isAvailable()).isFalse();
            assertThat(item.unavailableReason()).isEqualTo(AvailabilityCheckResult.REASON_ALREADY_OWNED);
        });
    }

    @Test
    @DisplayName("UTCID10 [B]: Existing cart with zero items does not recreate cart and skips product lookup")
    void execute_existingEmptyCart_returnsEmptyItemsWithoutRecreatingCart() {
        Long customerId = 42L;
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CartEntity cart = CartEntity.of(101L, customerId, now);

        when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findAllByCart_CartIdOrderByAddedAtDesc(101L)).thenReturn(List.of());

        CartResponse response = viewCartUseCase.execute(customerId);

        assertThat(response.cartId()).isEqualTo(101L);
        assertThat(response.items()).isEmpty();
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(productLookupService, productAvailabilityPort);
    }
}
