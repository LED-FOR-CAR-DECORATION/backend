package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.CheckoutRequest;
import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.domain.event.OrderCreatedEvent;
import com.lumintra.backend.commerce.domain.model.AvailabilityCheckResult;
import com.lumintra.backend.commerce.domain.model.BundleCheckoutInspection;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.domain.service.PaymentIntentPort;
import com.lumintra.backend.commerce.domain.service.ProductAvailabilityPort;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.CartItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.CartRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderItemRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import com.lumintra.backend.device.domain.service.LedInventoryService;
import com.lumintra.backend.iam.domain.model.UserAddressSnapshot;
import com.lumintra.backend.iam.domain.service.UserAddressLookupService;
import com.lumintra.backend.shared.exception.commerce.AddressNotFoundException;
import com.lumintra.backend.shared.exception.commerce.DeliveryAddressRequiredException;
import com.lumintra.backend.shared.exception.commerce.DigitalItemQuantityLimitException;
import com.lumintra.backend.shared.exception.commerce.InsufficientStockException;
import com.lumintra.backend.shared.exception.commerce.InvalidCartItemException;
import com.lumintra.backend.shared.exception.commerce.ProductAlreadyOwnedException;
import com.lumintra.backend.shared.exception.commerce.ProductNotAvailableException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CheckoutOrderUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckoutOrderUseCase.class);
    private static final long PAYMENT_EXPIRATION_MINUTES = 15L;
    private static final BigDecimal FREE_SHIPPING_FEE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderDeliveryRepository orderDeliveryRepository;
    private final ProductLookupService productLookupService;
    private final ProductAvailabilityPort productAvailabilityPort;
    private final LedInventoryService ledInventoryService;
    private final UserAddressLookupService userAddressLookupService;
    private final PaymentIntentPort paymentIntentPort;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CheckoutResponse execute(Long customerId, CheckoutRequest request) {
        CartEntity cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new InvalidCartItemException("Shopping cart not found or selected items are invalid."));

        Set<Long> requestedItemIds = new LinkedHashSet<>(request.cartItemIds());
        List<CartItemEntity> selectedItems =
                cartItemRepository.findAllByCart_CartIdAndCartItemIdIn(cart.getCartId(), requestedItemIds);

        if (selectedItems.size() != requestedItemIds.size()) {
            throw new InvalidCartItemException("One or more selected cart items do not exist or do not belong to your cart.");
        }

        Map<Long, ProductSnapshot> productsById = productLookupService.findSnapshotsByIds(
                selectedItems.stream().map(CartItemEntity::getProductId).collect(Collectors.toSet()));

        CheckoutInspection inspection = inspectSelectedItems(customerId, selectedItems, productsById);

        ResolvedShippingAddress shippingAddress = inspection.hasPhysicalGoods()
                ? resolveShippingAddress(customerId, request)
                : null;

        if (!inspection.requiredLedStocks().isEmpty()
                && !ledInventoryService.deductStocksIfAvailable(inspection.requiredLedStocks())) {
            throw new InsufficientStockException("Insufficient LED hardware stock for one or more selected items.");
        }

        Instant expiresAt = Instant.now().plus(PAYMENT_EXPIRATION_MINUTES, ChronoUnit.MINUTES);
        Long deliveryAddressId = shippingAddress != null ? shippingAddress.deliveryAddressId() : null;

        OrderEntity order = orderRepository.save(OrderEntity.createPendingPayment(
                customerId,
                deliveryAddressId,
                inspection.subtotal(),
                FREE_SHIPPING_FEE,
                inspection.subtotal(),
                inspection.orderType(),
                expiresAt
        ));

        List<OrderItemEntity> orderItems = new ArrayList<>(selectedItems.size());
        for (CartItemEntity cartItem : selectedItems) {
            ProductSnapshot product = productsById.get(cartItem.getProductId());
            orderItems.add(OrderItemEntity.create(
                    order,
                    product.productId(),
                    cartItem.getQuantity(),
                    product.basePrice()
            ));
        }
        orderItemRepository.saveAll(orderItems);

        if (shippingAddress != null) {
            orderDeliveryRepository.save(OrderDeliveryEntity.createPending(order, shippingAddress));
        }

        PaymentIntentResult paymentIntent = paymentIntentPort.initializePaymentIntent(
                order.getOrderId(),
                order.getTotalAmount(),
                request.paymentGateway()
        );

        cartItemRepository.deleteAllByIdInBatch(requestedItemIds);
        cart.touch();
        cartRepository.save(cart);

        eventPublisher.publishEvent(new OrderCreatedEvent(
                order.getOrderId(),
                customerId,
                paymentIntent.paymentId(),
                paymentIntent.gateway(),
                order.getTotalAmount(),
                order.getExpiresAt()
        ));

        log.info("Order created successfully [orderId={}] [customerId={}] [orderType={}] [paymentId={}]",
                order.getOrderId(), customerId, order.getOrderType(), paymentIntent.paymentId());

        return orderMapper.toCheckoutResponse(order, paymentIntent);
    }

    private CheckoutInspection inspectSelectedItems(
            Long customerId,
            List<CartItemEntity> selectedItems,
            Map<Long, ProductSnapshot> productsById) {
        boolean hasPhysicalGoods = false;
        Map<Long, Long> requiredLedStocks = new HashMap<>();
        Set<ProductType> distinctProductTypes = EnumSet.noneOf(ProductType.class);
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItemEntity cartItem : selectedItems) {
            ProductSnapshot product = productsById.get(cartItem.getProductId());
            if (product == null || product.status() != ProductStatus.PUBLISHED) {
                throw new ProductNotAvailableException("One or more selected products are not available for purchase.");
            }

            ProductType type = product.productType();
            distinctProductTypes.add(type);

            if (type == ProductType.CHARACTER || type == ProductType.SUBSCRIPTION) {
                if (cartItem.getQuantity() > 1) {
                    throw new DigitalItemQuantityLimitException("Digital products cannot exceed a quantity of 1.");
                }
                if (productAvailabilityPort.isDigitalProductOwned(customerId, product.productId(), type)) {
                    throw new ProductAlreadyOwnedException("Customer already owns one or more selected digital products.");
                }
            } else if (type == ProductType.LED) {
                hasPhysicalGoods = true;
                requiredLedStocks.merge(product.productId(), (long) cartItem.getQuantity(), Long::sum);
            } else if (type == ProductType.BUNDLE) {
                BundleCheckoutInspection bundleInspection =
                        productAvailabilityPort.inspectBundleForCheckout(customerId, product.productId(), cartItem.getQuantity());
                if (!bundleInspection.available()) {
                    throw translateBundleFailure(bundleInspection.unavailableReason());
                }
                if (bundleInspection.containsPhysicalLed()) {
                    hasPhysicalGoods = true;
                    bundleInspection.requiredLedStocksByModelId()
                            .forEach((modelId, qty) -> requiredLedStocks.merge(modelId, qty, Long::sum));
                }
            } else {
                throw new ProductNotAvailableException("Unsupported product type in cart.");
            }

            BigDecimal lineSubtotal = product.basePrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            subtotal = subtotal.add(lineSubtotal);
        }

        OrderType orderType = resolveOrderType(distinctProductTypes);
        return new CheckoutInspection(hasPhysicalGoods, requiredLedStocks, orderType, subtotal.setScale(2, RoundingMode.HALF_UP));
    }

    private RuntimeException translateBundleFailure(String reason) {
        if (AvailabilityCheckResult.REASON_ALREADY_OWNED.equals(reason)) {
            return new ProductAlreadyOwnedException("Customer already owns a character included in the selected bundle.");
        }
        if (AvailabilityCheckResult.REASON_OUT_OF_STOCK.equals(reason)) {
            return new InsufficientStockException("Insufficient LED hardware stock for the selected bundle.");
        }
        return new ProductNotAvailableException("Selected bundle is unavailable or outside its active window.");
    }

    private ResolvedShippingAddress resolveShippingAddress(Long customerId, CheckoutRequest request) {
        if (request.deliveryAddressId() != null) {
            UserAddressSnapshot savedAddress = userAddressLookupService
                    .findByIdAndCustomerId(request.deliveryAddressId(), customerId)
                    .orElseThrow(() -> new AddressNotFoundException("Delivery address not found."));
            return new ResolvedShippingAddress(
                    savedAddress.addressId(),
                    savedAddress.recipientName(),
                    savedAddress.recipientPhone(),
                    savedAddress.fullAddress(),
                    savedAddress.latitude(),
                    savedAddress.longitude()
            );
        }

        if (request.shippingDetails() != null) {
            CheckoutRequest.ShippingDetailsRequest inline = request.shippingDetails();
            return new ResolvedShippingAddress(
                    null,
                    inline.recipientName().trim(),
                    inline.recipientPhone().trim(),
                    inline.fullAddress().trim(),
                    inline.latitude(),
                    inline.longitude()
            );
        }

        throw new DeliveryAddressRequiredException("Delivery address or shipping details are required for physical orders.");
    }

    private OrderType resolveOrderType(Set<ProductType> distinctProductTypes) {
        if (distinctProductTypes.size() > 1) {
            return OrderType.MIXED;
        }
        ProductType singleType = distinctProductTypes.iterator().next();
        return switch (singleType) {
            case LED -> OrderType.PHYSICAL;
            case CHARACTER -> OrderType.DIGITAL;
            case BUNDLE -> OrderType.BUNDLE;
            case SUBSCRIPTION -> OrderType.SUBSCRIPTION;
        };
    }

    private record CheckoutInspection(
            boolean hasPhysicalGoods,
            Map<Long, Long> requiredLedStocks,
            OrderType orderType,
            BigDecimal subtotal
    ) {}
}
