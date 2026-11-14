package com.lumintra.backend.commerce.application;

import com.lumintra.backend.commerce.api.request.AhamoveWebhookRequest;
import com.lumintra.backend.commerce.api.response.AhamoveWebhookResponse;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.infrastructure.integration.AhamoveProperties;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(AhamoveProperties.class)
public class ProcessAhamoveWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessAhamoveWebhookUseCase.class);

    private static final String OUTCOME_PROCESSED = "PROCESSED";
    private static final String OUTCOME_IGNORED = "IGNORED";
    private static final String OUTCOME_UNMATCHED = "UNMATCHED";

    private static final BigDecimal MILLIS_EPOCH_THRESHOLD = BigDecimal.valueOf(1_000_000_000_000L);

    private static final Set<OrderStatus> TERMINAL_ORDER_STATUSES = Set.of(
            OrderStatus.DELIVERED,
            OrderStatus.COMPLETED,
            OrderStatus.CANCELED,
            OrderStatus.REFUNDED
    );

    private static final Set<DeliveryStatus> ALLOWED_DELIVERY_FOR_ASSIGNED = Set.of(
            DeliveryStatus.PENDING
    );

    private static final Set<DeliveryStatus> ALLOWED_DELIVERY_FOR_SHIPPING = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.PICKED_UP
    );

    private static final Set<DeliveryStatus> ALLOWED_DELIVERY_FOR_DELIVERED = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.SHIPPING
    );

    private static final Set<DeliveryStatus> ALLOWED_DELIVERY_FOR_FAILED = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.SHIPPING
    );

    private static final Set<DeliveryStatus> ALLOWED_DELIVERY_FOR_RETURNED = Set.of(
            DeliveryStatus.PENDING,
            DeliveryStatus.ASSIGNED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.SHIPPING,
            DeliveryStatus.FAILED
    );

    private static final Set<OrderStatus> ALLOWED_ORDER_FOR_SHIPPING = Set.of(
            OrderStatus.PROCESSING,
            OrderStatus.PARTIALLY_FULFILLED
    );

    private static final Set<OrderStatus> ALLOWED_ORDER_FOR_FINAL_OUTCOME = Set.of(
            OrderStatus.PROCESSING,
            OrderStatus.PARTIALLY_FULFILLED,
            OrderStatus.SHIPPING
    );

    private static final Set<String> FAILURE_SUB_STATUSES = Set.of(
            "FAILED",
            "CANCELLED",
            "CANCELED",
            "DELIVERY_FAILED",
            "LOST",
            "DAMAGED"
    );

    private final OrderDeliveryRepository orderDeliveryRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final AhamoveProperties ahamoveProperties;

    @Transactional
    public AhamoveWebhookResponse execute(HttpHeaders headers, AhamoveWebhookRequest request) {
        verifyWebhookCredentials(headers);

        String rawTracking = request.rawTrackingNumber();
        String baseTracking = request.baseTrackingNumber();
        String displayTracking = baseTracking != null ? baseTracking : rawTracking;

        Optional<OrderDeliveryEntity> deliveryOpt = findMatchingDelivery(rawTracking, baseTracking);
        if (deliveryOpt.isEmpty()) {
            log.warn("AhaMove webhook ignored: unmatched tracking number [trackingNumber={}] [status={}]",
                    displayTracking, request.status());
            return orderMapper.toAhamoveWebhookResponse(
                    displayTracking,
                    OUTCOME_UNMATCHED,
                    null,
                    null,
                    "No delivery matched the webhook tracking number."
            );
        }

        OrderDeliveryEntity delivery = deliveryOpt.get();
        OrderEntity order = delivery.getOrder();
        DeliveryStatus currentDeliveryStatus = delivery.getDeliveryStatus();
        OrderStatus currentOrderStatus = order.getStatus();

        Optional<WebhookTransition> transitionOpt = resolveTransition(request);
        if (transitionOpt.isEmpty()) {
            log.info("AhaMove webhook ignored: unsupported carrier status [orderId={}] [trackingNumber={}] [status={}]",
                    order.getOrderId(), displayTracking, request.status());
            return orderMapper.toAhamoveWebhookResponse(
                    delivery.getTrackingNumber(),
                    OUTCOME_IGNORED,
                    currentDeliveryStatus,
                    currentOrderStatus,
                    "Unsupported webhook carrier status ignored."
            );
        }

        if (TERMINAL_ORDER_STATUSES.contains(currentOrderStatus)) {
            log.info("AhaMove webhook ignored: order is already in terminal state [orderId={}] [orderStatus={}] [deliveryStatus={}]",
                    order.getOrderId(), currentOrderStatus, currentDeliveryStatus);
            return orderMapper.toAhamoveWebhookResponse(
                    delivery.getTrackingNumber(),
                    OUTCOME_IGNORED,
                    currentDeliveryStatus,
                    currentOrderStatus,
                    "Webhook ignored because order is already in a terminal state."
            );
        }

        WebhookTransition transition = transitionOpt.get();
        Instant now = Instant.now();
        Instant deliveredAt = transition.targetDeliveryStatus() == DeliveryStatus.DELIVERED
                ? resolveCompletedAt(request.completeTime(), now)
                : null;

        int deliveryUpdated = orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                delivery.getDeliveryId(),
                transition.allowedDeliveryStatuses(),
                transition.targetDeliveryStatus(),
                request.normalizedTrackingUrl(),
                request.resolvedCarrierFee(),
                deliveredAt,
                now
        );

        int orderUpdated = orderRepository.updateStatusIfIn(
                order.getOrderId(),
                transition.allowedOrderStatuses(),
                transition.targetOrderStatus(),
                now
        );

        if (deliveryUpdated == 0 && orderUpdated == 0) {
            log.info("AhaMove webhook idempotent or out-of-order no-op [orderId={}] [currentOrderStatus={}] [currentDeliveryStatus={}] [targetOrderStatus={}] [targetDeliveryStatus={}]",
                    order.getOrderId(), currentOrderStatus, currentDeliveryStatus,
                    transition.targetOrderStatus(), transition.targetDeliveryStatus());
            return orderMapper.toAhamoveWebhookResponse(
                    delivery.getTrackingNumber(),
                    OUTCOME_IGNORED,
                    currentDeliveryStatus,
                    currentOrderStatus,
                    "Webhook event ignored as duplicate or non-monotonic transition."
            );
        }

        DeliveryStatus effectiveDeliveryStatus = deliveryUpdated > 0
                ? transition.targetDeliveryStatus()
                : currentDeliveryStatus;
        OrderStatus effectiveOrderStatus = orderUpdated > 0
                ? transition.targetOrderStatus()
                : currentOrderStatus;

        log.info("AhaMove webhook processed [orderId={}] [trackingNumber={}] [deliveryStatus={}] [orderStatus={}]",
                order.getOrderId(), delivery.getTrackingNumber(), effectiveDeliveryStatus, effectiveOrderStatus);

        return orderMapper.toAhamoveWebhookResponse(
                delivery.getTrackingNumber(),
                OUTCOME_PROCESSED,
                effectiveDeliveryStatus,
                effectiveOrderStatus,
                "Webhook processed successfully."
        );
    }

    private void verifyWebhookCredentials(HttpHeaders headers) {
        if (!ahamoveProperties.isWebhookAuthRequired()) {
            log.warn("AhaMove webhook authentication skipped because app.ahamove.webhook.require-auth=false");
            return;
        }

        String expectedToken = ahamoveProperties.resolvedWebhookToken();
        if (expectedToken == null) {
            log.error("AhaMove webhook verification failed: webhook token is not configured");
            throw new BadCredentialsException("Invalid AhaMove webhook credentials.");
        }

        String configuredHeaderName = ahamoveProperties.normalizedWebhookHeaderName();
        String configuredHeaderValue = headers != null ? headers.getFirst(configuredHeaderName) : null;
        String authorizationHeaderValue = headers != null ? headers.getFirst(HttpHeaders.AUTHORIZATION) : null;

        boolean validConfiguredHeader = matchesConfiguredCredential(configuredHeaderValue, expectedToken);
        boolean validBearerHeader = !HttpHeaders.AUTHORIZATION.equalsIgnoreCase(configuredHeaderName)
                && matchesBearerCredential(authorizationHeaderValue, expectedToken);

        if (!validConfiguredHeader && !validBearerHeader) {
            log.warn("AhaMove webhook verification failed: missing or invalid token [configuredHeaderPresent={}] [authorizationPresent={}]",
                    configuredHeaderValue != null && !configuredHeaderValue.isBlank(),
                    authorizationHeaderValue != null && !authorizationHeaderValue.isBlank());
            throw new BadCredentialsException("Invalid AhaMove webhook credentials.");
        }
    }

    private boolean matchesConfiguredCredential(String rawValue, String expectedToken) {
        if (rawValue == null || rawValue.isBlank()) {
            return false;
        }
        String candidate = stripAuthScheme(rawValue.trim());
        return constantTimeEquals(candidate, expectedToken);
    }

    private boolean matchesBearerCredential(String authorizationValue, String expectedToken) {
        if (authorizationValue == null || authorizationValue.isBlank()) {
            return false;
        }
        String trimmed = authorizationValue.trim();
        if (trimmed.length() <= 7 || !trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String candidate = trimmed.substring(7).trim();
        return constantTimeEquals(candidate, expectedToken);
    }

    private String stripAuthScheme(String value) {
        if (value.length() > 7 && value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        if (value.length() > 7 && value.regionMatches(true, 0, "Apikey ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private boolean constantTimeEquals(String candidate, String expected) {
        if (candidate == null || candidate.isBlank() || expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Optional<OrderDeliveryEntity> findMatchingDelivery(String rawTracking, String baseTracking) {
        if (rawTracking == null) {
            return Optional.empty();
        }
        Optional<OrderDeliveryEntity> exact = orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc(rawTracking);
        if (exact.isPresent()) {
            return exact;
        }
        if (baseTracking != null && !baseTracking.equals(rawTracking)) {
            return orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc(baseTracking);
        }
        return Optional.empty();
    }

    private Optional<WebhookTransition> resolveTransition(AhamoveWebhookRequest request) {
        String normalizedStatus = request.status().trim().toUpperCase(Locale.ROOT);
        String normalizedSubStatus = request.subStatus() != null
                ? request.subStatus().trim().toUpperCase(Locale.ROOT)
                : "";

        if ("RETURNED".equals(normalizedSubStatus)) {
            return Optional.of(new WebhookTransition(
                    DeliveryStatus.RETURNED,
                    OrderStatus.DELIVERY_FAILED,
                    ALLOWED_DELIVERY_FOR_RETURNED,
                    ALLOWED_ORDER_FOR_FINAL_OUTCOME
            ));
        }

        return switch (normalizedStatus) {
            case "ASSIGNING", "ACCEPTED" -> Optional.of(new WebhookTransition(
                    DeliveryStatus.ASSIGNED,
                    OrderStatus.SHIPPING,
                    ALLOWED_DELIVERY_FOR_ASSIGNED,
                    ALLOWED_ORDER_FOR_SHIPPING
            ));
            case "IN PROCESS", "IN_PROCESS" -> {
                if (FAILURE_SUB_STATUSES.contains(normalizedSubStatus)) {
                    yield Optional.of(failedTransition());
                }
                yield Optional.of(new WebhookTransition(
                        DeliveryStatus.SHIPPING,
                        OrderStatus.SHIPPING,
                        ALLOWED_DELIVERY_FOR_SHIPPING,
                        ALLOWED_ORDER_FOR_SHIPPING
                ));
            }
            case "COMPLETED" -> {
                if (FAILURE_SUB_STATUSES.contains(normalizedSubStatus) || hasDropoffFailureWithoutSuccess(request)) {
                    yield Optional.of(failedTransition());
                }
                yield Optional.of(new WebhookTransition(
                        DeliveryStatus.DELIVERED,
                        OrderStatus.DELIVERED,
                        ALLOWED_DELIVERY_FOR_DELIVERED,
                        ALLOWED_ORDER_FOR_FINAL_OUTCOME
                ));
            }
            case "CANCELLED", "CANCELED", "FAILED" -> Optional.of(failedTransition());
            default -> Optional.empty();
        };
    }

    private WebhookTransition failedTransition() {
        return new WebhookTransition(
                DeliveryStatus.FAILED,
                OrderStatus.DELIVERY_FAILED,
                ALLOWED_DELIVERY_FOR_FAILED,
                ALLOWED_ORDER_FOR_FINAL_OUTCOME
        );
    }

    private boolean hasDropoffFailureWithoutSuccess(AhamoveWebhookRequest request) {
        List<AhamoveWebhookRequest.PathPoint> path = request.path();
        if (path == null || path.isEmpty()) {
            return false;
        }
        List<AhamoveWebhookRequest.PathPoint> dropoffs;
        if (path.size() > 1) {
            dropoffs = path.subList(1, path.size());
        } else if (request.hasStopIndexSuffix()) {
            dropoffs = path;
        } else {
            return false;
        }

        boolean hasFailedStop = dropoffs.stream()
                .filter(point -> point != null && point.status() != null)
                .anyMatch(point -> "FAILED".equalsIgnoreCase(point.status().trim()));
        boolean hasCompletedStop = dropoffs.stream()
                .filter(point -> point != null && point.status() != null)
                .anyMatch(point -> "COMPLETED".equalsIgnoreCase(point.status().trim()));

        return hasFailedStop && !hasCompletedStop;
    }

    private Instant resolveCompletedAt(BigDecimal completeTime, Instant fallback) {
        if (completeTime == null || completeTime.compareTo(BigDecimal.ZERO) <= 0) {
            return fallback;
        }
        if (completeTime.compareTo(MILLIS_EPOCH_THRESHOLD) >= 0) {
            return Instant.ofEpochMilli(completeTime.longValue());
        }
        return Instant.ofEpochSecond(completeTime.longValue());
    }

    private record WebhookTransition(
            DeliveryStatus targetDeliveryStatus,
            OrderStatus targetOrderStatus,
            Set<DeliveryStatus> allowedDeliveryStatuses,
            Set<OrderStatus> allowedOrderStatuses
    ) {}
}
