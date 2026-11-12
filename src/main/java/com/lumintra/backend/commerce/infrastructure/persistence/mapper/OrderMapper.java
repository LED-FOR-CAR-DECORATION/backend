package com.lumintra.backend.commerce.infrastructure.persistence.mapper;

import com.lumintra.backend.commerce.api.response.CheckoutResponse;
import com.lumintra.backend.commerce.api.response.OrderDetailResponse;
import com.lumintra.backend.commerce.api.response.OrderListResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderDetailResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderListResponse;
import com.lumintra.backend.commerce.api.response.StaffOrderStatusResponse;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.PaymentIntentResult;
import com.lumintra.backend.commerce.domain.model.PaymentStatus;
import com.lumintra.backend.commerce.domain.model.SepayPaymentInstructions;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderItemEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.PaymentTransactionEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliverySummaryRow;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.shared.response.PaginationMeta;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.model.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class OrderMapper {

    public CheckoutResponse toCheckoutResponse(OrderEntity order, PaymentIntentResult paymentIntent) {
        if (order == null) {
            return null;
        }
        return new CheckoutResponse(
                order.getOrderId(),
                paymentIntent != null ? paymentIntent.paymentId() : null,
                paymentIntent != null ? paymentIntent.gateway() : null,
                order.getTotalAmount(),
                order.getStatus(),
                order.getExpiresAt()
        );
    }

    public OrderListResponse toOrderListResponse(
            Page<OrderEntity> page,
            Map<Long, Integer> itemCountsByOrderId,
            Map<Long, Long> primaryProductIdByOrderId,
            Map<Long, ProductSnapshot> productsById) {
        if (page == null) {
            return new OrderListResponse(Collections.emptyList(), null);
        }

        List<OrderListResponse.OrderSummaryItem> items = page.getContent().stream()
                .map(order -> toOrderSummaryItem(
                        order,
                        itemCountsByOrderId != null ? itemCountsByOrderId.getOrDefault(order.getOrderId(), 0) : 0,
                        primaryProductIdByOrderId != null && productsById != null
                                ? productsById.get(primaryProductIdByOrderId.get(order.getOrderId()))
                                : null
                ))
                .toList();

        return new OrderListResponse(items, PaginationMeta.from(page));
    }

    public OrderListResponse.OrderSummaryItem toOrderSummaryItem(
            OrderEntity order,
            int totalItems,
            ProductSnapshot primaryProduct) {
        if (order == null) {
            return null;
        }

        Instant visibleExpiresAt = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getExpiresAt()
                : null;

        return OrderListResponse.OrderSummaryItem.builder()
                .orderId(order.getOrderId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .totalItems(totalItems)
                .primaryProductName(primaryProduct != null ? primaryProduct.productName() : "Unavailable Product")
                .primaryProductThumbnail(primaryProduct != null ? primaryProduct.thumbnailUrl() : null)
                .expiresAt(visibleExpiresAt)
                .createdAt(order.getCreatedAt())
                .build();
    }

    public StaffOrderListResponse toStaffOrderListResponse(
            Page<OrderEntity> page,
            Map<Long, Integer> itemCountsByOrderId,
            Map<Long, Long> primaryProductIdByOrderId,
            Map<Long, ProductSnapshot> productsById,
            Map<Long, OrderDeliverySummaryRow> deliveriesByOrderId,
            Map<Long, PaymentStatus> paymentStatusesByOrderId,
            Map<Long, CustomerProfileSnapshot> customersById) {
        if (page == null) {
            return new StaffOrderListResponse(Collections.emptyList(), null);
        }

        List<StaffOrderListResponse.StaffOrderSummaryItem> items = page.getContent().stream()
                .map(order -> {
                    Long orderId = order.getOrderId();
                    int totalItems = itemCountsByOrderId != null ? itemCountsByOrderId.getOrDefault(orderId, 0) : 0;
                    ProductSnapshot primaryProduct = (primaryProductIdByOrderId != null && productsById != null)
                            ? productsById.get(primaryProductIdByOrderId.get(orderId))
                            : null;
                    OrderDeliverySummaryRow delivery = deliveriesByOrderId != null ? deliveriesByOrderId.get(orderId) : null;
                    PaymentStatus paymentStatus = paymentStatusesByOrderId != null ? paymentStatusesByOrderId.get(orderId) : null;
                    CustomerProfileSnapshot customer = customersById != null ? customersById.get(order.getCustomerId()) : null;
                    return toStaffOrderSummaryItem(order, totalItems, primaryProduct, delivery, paymentStatus, customer);
                })
                .toList();

        return new StaffOrderListResponse(items, PaginationMeta.from(page));
    }

    public StaffOrderListResponse.StaffOrderSummaryItem toStaffOrderSummaryItem(
            OrderEntity order,
            int totalItems,
            ProductSnapshot primaryProduct,
            OrderDeliverySummaryRow delivery,
            PaymentStatus paymentStatus,
            CustomerProfileSnapshot customer) {
        if (order == null) {
            return null;
        }

        return StaffOrderListResponse.StaffOrderSummaryItem.builder()
                .orderId(order.getOrderId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .paymentStatus(paymentStatus)
                .deliveryStatus(delivery != null ? delivery.deliveryStatus() : null)
                .customerId(order.getCustomerId())
                .customerFullName(customer != null ? customer.fullName() : null)
                .customerEmail(customer != null ? customer.email() : null)
                .recipientName(delivery != null ? delivery.recipientName() : null)
                .recipientPhone(delivery != null ? delivery.recipientPhone() : null)
                .totalAmount(order.getTotalAmount())
                .totalItems(totalItems)
                .primaryProductName(primaryProduct != null ? primaryProduct.productName() : "Unavailable Product")
                .primaryProductThumbnail(primaryProduct != null ? primaryProduct.thumbnailUrl() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    public OrderDetailResponse toOrderDetailResponse(
            OrderEntity order,
            List<OrderItemEntity> items,
            Map<Long, ProductSnapshot> productsById,
            Map<Long, String> previewGifUrlsByProductId,
            OrderDeliveryEntity delivery,
            PaymentTransactionEntity payment,
            SepayPaymentInstructions paymentInstructions) {
        if (order == null) {
            return null;
        }

        Instant visibleExpiresAt = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getExpiresAt()
                : null;

        List<OrderDetailResponse.OrderItemDetail> itemDetails = items == null
                ? Collections.emptyList()
                : items.stream()
                        .map(item -> toOrderItemDetail(
                                item,
                                productsById != null ? productsById.get(item.getProductId()) : null,
                                previewGifUrlsByProductId
                        ))
                        .toList();

        return OrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .expiresAt(visibleExpiresAt)
                .createdAt(order.getCreatedAt())
                .items(itemDetails)
                .delivery(toDeliveryDetail(delivery))
                .payment(toPaymentDetail(order, payment, paymentInstructions))
                .build();
    }

    private OrderDetailResponse.OrderItemDetail toOrderItemDetail(
            OrderItemEntity item,
            ProductSnapshot product,
            Map<Long, String> previewGifUrlsByProductId) {
        ProductType productType = product != null ? product.productType() : null;
        String previewGifUrl = (productType == ProductType.CHARACTER && previewGifUrlsByProductId != null)
                ? previewGifUrlsByProductId.get(item.getProductId())
                : null;

        return OrderDetailResponse.OrderItemDetail.builder()
                .orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .productName(product != null ? product.productName() : "Unavailable Product")
                .productType(productType)
                .thumbnailUrl(product != null ? product.thumbnailUrl() : null)
                .previewGifUrl(previewGifUrl)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

    private OrderDetailResponse.DeliveryDetail toDeliveryDetail(OrderDeliveryEntity delivery) {
        if (delivery == null) {
            return null;
        }

        return OrderDetailResponse.DeliveryDetail.builder()
                .carrier(delivery.getCarrier())
                .recipientName(delivery.getRecipientName())
                .recipientPhone(delivery.getRecipientPhone())
                .fullAddress(delivery.getFullAddress())
                .latitude(delivery.getLatitude())
                .longitude(delivery.getLongitude())
                .deliveryStatus(delivery.getDeliveryStatus())
                .trackingNumber(delivery.getTrackingNumber())
                .trackingUrl(delivery.getTrackingUrl())
                .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                .build();
    }

    private OrderDetailResponse.PaymentDetail toPaymentDetail(
            OrderEntity order,
            PaymentTransactionEntity payment,
            SepayPaymentInstructions paymentInstructions) {
        if (payment == null) {
            return null;
        }

        if (paymentInstructions != null) {
            return OrderDetailResponse.PaymentDetail.builder()
                    .paymentGateway(payment.getGateway())
                    .status(payment.getStatus())
                    .paymentContent(paymentInstructions.paymentContent())
                    .qrCodeUrl(paymentInstructions.qrCodeUrl())
                    .bankAccount(paymentInstructions.bankAccount())
                    .bankName(paymentInstructions.bankName())
                    .expiresAt(paymentInstructions.expiresAt())
                    .build();
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return OrderDetailResponse.PaymentDetail.builder()
                    .paymentGateway(payment.getGateway())
                    .status(payment.getStatus())
                    .bankReferenceCode(payment.getTransactionCode())
                    .paidAmount(payment.getAmount())
                    .paidAt(payment.getPaidAt())
                    .build();
        }

        Instant expiredPendingAt = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getExpiresAt()
                : null;

        return OrderDetailResponse.PaymentDetail.builder()
                .paymentGateway(payment.getGateway())
                .status(payment.getStatus())
                .expiresAt(expiredPendingAt)
                .build();
    }

    public StaffOrderDetailResponse toStaffOrderDetailResponse(
            OrderEntity order,
            CustomerProfileSnapshot customer,
            List<OrderItemEntity> items,
            Map<Long, ProductSnapshot> productsById,
            Map<Long, String> previewGifUrlsByProductId,
            OrderDeliveryEntity delivery,
            PaymentTransactionEntity payment) {
        if (order == null) {
            return null;
        }

        Instant visibleExpiresAt = order.getStatus() == OrderStatus.PENDING_PAYMENT
                ? order.getExpiresAt()
                : null;

        StaffOrderDetailResponse.CustomerSummary customerSummary = new StaffOrderDetailResponse.CustomerSummary(
                order.getCustomerId(),
                customer != null ? customer.fullName() : null,
                customer != null ? customer.phone() : null,
                customer != null ? customer.email() : null
        );

        List<StaffOrderDetailResponse.OrderItemDetail> itemDetails = items == null
                ? Collections.emptyList()
                : items.stream()
                        .map(item -> toStaffOrderItemDetail(
                                item,
                                productsById != null ? productsById.get(item.getProductId()) : null,
                                previewGifUrlsByProductId
                        ))
                        .toList();

        return StaffOrderDetailResponse.builder()
                .orderId(order.getOrderId())
                .orderType(order.getOrderType())
                .status(order.getStatus())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .totalAmount(order.getTotalAmount())
                .expiresAt(visibleExpiresAt)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .customer(customerSummary)
                .items(itemDetails)
                .delivery(toStaffDeliveryDetail(delivery))
                .payment(toStaffPaymentDetail(payment))
                .build();
    }

    private StaffOrderDetailResponse.OrderItemDetail toStaffOrderItemDetail(
            OrderItemEntity item,
            ProductSnapshot product,
            Map<Long, String> previewGifUrlsByProductId) {
        ProductType productType = product != null ? product.productType() : null;
        String previewGifUrl = (productType == ProductType.CHARACTER && previewGifUrlsByProductId != null)
                ? previewGifUrlsByProductId.get(item.getProductId())
                : null;

        return StaffOrderDetailResponse.OrderItemDetail.builder()
                .orderItemId(item.getOrderItemId())
                .productId(item.getProductId())
                .productName(product != null ? product.productName() : "Unavailable Product")
                .productType(productType)
                .thumbnailUrl(product != null ? product.thumbnailUrl() : null)
                .previewGifUrl(previewGifUrl)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .build();
    }

    private StaffOrderDetailResponse.DeliveryDetail toStaffDeliveryDetail(OrderDeliveryEntity delivery) {
        if (delivery == null) {
            return null;
        }

        return StaffOrderDetailResponse.DeliveryDetail.builder()
                .carrier(delivery.getCarrier())
                .recipientName(delivery.getRecipientName())
                .recipientPhone(delivery.getRecipientPhone())
                .fullAddress(delivery.getFullAddress())
                .latitude(delivery.getLatitude())
                .longitude(delivery.getLongitude())
                .deliveryStatus(delivery.getDeliveryStatus())
                .trackingNumber(delivery.getTrackingNumber())
                .trackingUrl(delivery.getTrackingUrl())
                .carrierFee(delivery.getCarrierFee())
                .estimatedDeliveryAt(delivery.getEstimatedDeliveryAt())
                .deliveredAt(delivery.getDeliveredAt())
                .build();
    }

    private StaffOrderDetailResponse.StaffPaymentDetail toStaffPaymentDetail(PaymentTransactionEntity payment) {
        if (payment == null) {
            return null;
        }

        boolean settled = payment.getStatus() == PaymentStatus.SUCCESS;

        return StaffOrderDetailResponse.StaffPaymentDetail.builder()
                .paymentGateway(payment.getGateway())
                .status(payment.getStatus())
                .bankReferenceCode(settled ? payment.getTransactionCode() : null)
                .paidAmount(settled ? payment.getAmount() : null)
                .paidAt(settled ? payment.getPaidAt() : null)
                .build();
    }

    public StaffOrderStatusResponse toStaffOrderStatusResponse(
            Long orderId,
            OrderStatus previousStatus,
            OrderStatus status,
            Instant updatedAt) {
        if (orderId == null || status == null) {
            return null;
        }
        return new StaffOrderStatusResponse(orderId, previousStatus, status, updatedAt);
    }
}
