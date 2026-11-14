package com.lumintra.backend.commerce.infrastructure.integration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchCommand;
import com.lumintra.backend.commerce.domain.model.CarrierDispatchResult;
import com.lumintra.backend.commerce.domain.service.CarrierDispatchPort;
import com.lumintra.backend.shared.exception.commerce.CarrierDispatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Component
@EnableConfigurationProperties(AhamoveProperties.class)
public class AhamoveShippingAdapter implements CarrierDispatchPort {

    private static final Logger log = LoggerFactory.getLogger(AhamoveShippingAdapter.class);
    private static final Set<String> GROUP_PREFIXES = Set.of("VAN-", "TRUCK-", "2H-");

    private final RestClient restClient;
    private final AhamoveProperties properties;
    private final ReentrantLock tokenLock = new ReentrantLock();
    private volatile String cachedToken;

    @Autowired
    public AhamoveShippingAdapter(AhamoveProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(properties.normalizedBaseUrl())
                .requestFactory(factory)
                .build();
        this.properties = properties;
    }

    public AhamoveShippingAdapter(RestClient restClient, AhamoveProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public CarrierDispatchResult createDeliveryOrder(CarrierDispatchCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateConfiguration();

        AhamoveCreateOrderRequest payload = buildCreateOrderRequest(command);
        String token = getOrAcquireToken();

        try {
            return executeCreateOrder(command.orderId(), payload, token);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                log.warn("AhaMove partner token rejected with 401; refreshing token [orderId={}]", command.orderId());
                String refreshedToken = refreshToken(token);
                try {
                    return executeCreateOrder(command.orderId(), payload, refreshedToken);
                } catch (RestClientResponseException retryEx) {
                    throw mapResponseException(command.orderId(), retryEx);
                } catch (RestClientException retryEx) {
                    throw mapTransportException(command.orderId(), retryEx);
                }
            }
            throw mapResponseException(command.orderId(), ex);
        } catch (RestClientException ex) {
            throw mapTransportException(command.orderId(), ex);
        }
    }

    private CarrierDispatchResult executeCreateOrder(
            Long orderId,
            AhamoveCreateOrderRequest payload,
            String bearerToken) {
        AhamoveCreateOrderResponse response = restClient.post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .body(payload)
                .retrieve()
                .body(AhamoveCreateOrderResponse.class);

        if (response == null || !StringUtils.hasText(response.orderId())) {
            log.error("AhaMove returned empty order response [orderId={}]", orderId);
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_INVALID_RESPONSE",
                    "AhaMove returned an incomplete order creation response."
            );
        }

        BigDecimal carrierFee = extractCarrierFee(response);
        return new CarrierDispatchResult(
                response.orderId().trim(),
                StringUtils.hasText(response.sharedLink()) ? response.sharedLink().trim() : null,
                carrierFee,
                response.status()
        );
    }

    private String getOrAcquireToken() {
        String existing = cachedToken;
        if (StringUtils.hasText(existing)) {
            return existing;
        }
        tokenLock.lock();
        try {
            if (StringUtils.hasText(cachedToken)) {
                return cachedToken;
            }
            cachedToken = authenticateForToken();
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private String refreshToken(String staleToken) {
        tokenLock.lock();
        try {
            if (StringUtils.hasText(cachedToken) && !Objects.equals(cachedToken, staleToken)) {
                return cachedToken;
            }
            cachedToken = null;
            cachedToken = authenticateForToken();
            return cachedToken;
        } finally {
            tokenLock.unlock();
        }
    }

    private String authenticateForToken() {
        try {
            AhamoveTokenResponse tokenResponse = restClient.post()
                    .uri("/accounts/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AhamoveTokenRequest(properties.mobile().trim(), properties.apiKey().trim()))
                    .retrieve()
                    .body(AhamoveTokenResponse.class);

            if (tokenResponse == null || !StringUtils.hasText(tokenResponse.token())) {
                log.error("AhaMove token response did not include an access token");
                throw new CarrierDispatchException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "CARRIER_AUTH_FAILED",
                        "Failed to authenticate with AhaMove partner API."
                );
            }
            return tokenResponse.token().trim();
        } catch (RestClientResponseException ex) {
            AhamoveErrorPayload errorPayload = parseErrorPayload(ex);
            String providerCode = resolveProviderCode(errorPayload, "CARRIER_AUTH_FAILED");
            log.error("AhaMove partner authentication failed [httpStatus={}] [providerCode={}]",
                    ex.getStatusCode().value(), providerCode, ex);
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_AUTH_FAILED",
                    "Failed to authenticate with AhaMove partner API.",
                    ex
            );
        } catch (RestClientException ex) {
            log.error("AhaMove partner authentication network failure", ex);
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_UNAVAILABLE",
                    "AhaMove authentication service is temporarily unavailable.",
                    ex
            );
        }
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.apiKey()) || !StringUtils.hasText(properties.mobile())) {
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_NOT_CONFIGURED",
                    "AhaMove partner API credentials are not configured."
            );
        }
        if (properties.pickup() == null
                || !StringUtils.hasText(properties.pickup().address())
                || !StringUtils.hasText(properties.resolvedPickupMobile())) {
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_NOT_CONFIGURED",
                    "AhaMove warehouse pickup configuration is incomplete."
            );
        }
        if (!properties.isPaymentMethodValid()) {
            throw new CarrierDispatchException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CARRIER_NOT_CONFIGURED",
                    "Configured AhaMove payment method is invalid."
            );
        }
    }

    private AhamoveCreateOrderRequest buildCreateOrderRequest(CarrierDispatchCommand command) {
        AhamoveProperties.Pickup pickup = properties.pickup();
        AhamovePathPoint pickupPoint = new AhamovePathPoint(
                pickup.lat(),
                pickup.lng(),
                pickup.address().trim(),
                properties.resolvedPickupName(),
                properties.resolvedPickupMobile(),
                null,
                null
        );

        AhamovePathPoint recipientPoint = new AhamovePathPoint(
                command.recipientLatitude(),
                command.recipientLongitude(),
                command.recipientAddress(),
                command.recipientName(),
                command.recipientPhone(),
                0,
                command.internalTrackingNumber()
        );

        String normalizedServiceId = command.serviceId().trim().toUpperCase(Locale.ROOT);
        boolean isGroupService = isGroupServiceId(normalizedServiceId);

        List<AhamoveItem> items = (command.items() == null || command.items().isEmpty())
                ? null
                : command.items().stream()
                        .map(item -> new AhamoveItem(
                                "PROD-" + item.productId(),
                                item.name(),
                                item.subtotal() != null ? item.subtotal().longValue() : 0L,
                                item.quantity()
                        ))
                        .toList();

        List<AhamovePackageDetail> packageDetail = command.weightKg() != null
                ? List.of(new AhamovePackageDetail(command.weightKg()))
                : null;

        return new AhamoveCreateOrderRequest(
                0,
                List.of(pickupPoint, recipientPoint),
                isGroupService ? null : normalizedServiceId,
                isGroupService ? null : List.of(),
                isGroupService ? normalizedServiceId : null,
                isGroupService ? List.of() : null,
                properties.normalizedPaymentMethod(),
                command.remarks(),
                items,
                packageDetail
        );
    }

    static boolean isGroupServiceId(String serviceId) {
        if (!StringUtils.hasText(serviceId)) {
            return false;
        }
        String upper = serviceId.trim().toUpperCase(Locale.ROOT);
        if (!upper.contains("-")) {
            return true;
        }
        return GROUP_PREFIXES.stream().anyMatch(upper::startsWith);
    }

    private BigDecimal extractCarrierFee(AhamoveCreateOrderResponse response) {
        if (response.order() == null) {
            return null;
        }
        if (response.order().totalFee() != null) {
            return response.order().totalFee();
        }
        if (response.order().totalPrice() != null) {
            return response.order().totalPrice();
        }
        return response.order().totalPay();
    }

    private CarrierDispatchException mapResponseException(Long orderId, RestClientResponseException ex) {
        int rawStatus = ex.getStatusCode().value();
        AhamoveErrorPayload errorPayload = parseErrorPayload(ex);
        String defaultCode = defaultErrorCodeForStatus(rawStatus);
        String errorCode = resolveProviderCode(errorPayload, defaultCode);
        String message = resolveProviderMessage(errorPayload, rawStatus, errorCode);
        HttpStatus mappedStatus = mapHttpStatus(rawStatus);

        log.warn("AhaMove rejected dispatch [orderId={}] [httpStatus={}] [mappedStatus={}] [providerCode={}]",
                orderId, rawStatus, mappedStatus.value(), errorCode);

        return new CarrierDispatchException(mappedStatus, errorCode, message, ex);
    }

    private CarrierDispatchException mapTransportException(Long orderId, RestClientException ex) {
        if (ex instanceof ResourceAccessException) {
            log.error("AhaMove request timed out or connection failed [orderId={}]", orderId, ex);
        } else {
            log.error("AhaMove client communication failed [orderId={}]", orderId, ex);
        }
        return new CarrierDispatchException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "CARRIER_UNAVAILABLE",
                "AhaMove delivery service is temporarily unavailable.",
                ex
        );
    }

    private AhamoveErrorPayload parseErrorPayload(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(AhamoveErrorPayload.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String resolveProviderCode(AhamoveErrorPayload payload, String fallback) {
        if (payload != null) {
            if (StringUtils.hasText(payload.code())) {
                return payload.code().trim().toUpperCase(Locale.ROOT);
            }
            if (StringUtils.hasText(payload.error())) {
                return payload.error().trim().toUpperCase(Locale.ROOT);
            }
        }
        return fallback;
    }

    private String resolveProviderMessage(AhamoveErrorPayload payload, int rawStatus, String errorCode) {
        if (payload != null) {
            if (StringUtils.hasText(payload.description())) {
                return "AhaMove rejected dispatch (" + errorCode + "): " + payload.description().trim();
            }
            if (StringUtils.hasText(payload.message())) {
                return "AhaMove rejected dispatch (" + errorCode + "): " + payload.message().trim();
            }
        }
        if (rawStatus >= 500 || rawStatus == HttpStatus.UNAUTHORIZED.value()) {
            return "AhaMove delivery service is temporarily unavailable.";
        }
        return "AhaMove rejected delivery dispatch (" + errorCode + ").";
    }

    private HttpStatus mapHttpStatus(int rawStatus) {
        return switch (rawStatus) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 404 -> HttpStatus.NOT_FOUND;
            case 406 -> HttpStatus.NOT_ACCEPTABLE;
            case 409 -> HttpStatus.CONFLICT;
            case 401, 500, 502, 503, 504 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> rawStatus >= 400 && rawStatus < 500 ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    private String defaultErrorCodeForStatus(int rawStatus) {
        return switch (rawStatus) {
            case 400 -> "CARRIER_BAD_REQUEST";
            case 404 -> "CARRIER_RESOURCE_NOT_FOUND";
            case 406 -> "CARRIER_REJECTED";
            case 409 -> "CARRIER_CONFLICT";
            case 401 -> "CARRIER_AUTH_FAILED";
            default -> "CARRIER_UNAVAILABLE";
        };
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AhamoveTokenRequest(
            String mobile,
            @JsonProperty("api_key") String apiKey
    ) {}

    record AhamoveTokenResponse(
            String token,
            @JsonProperty("refresh_token") String refreshToken
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AhamoveCreateOrderRequest(
            @JsonProperty("order_time") long orderTime,
            List<AhamovePathPoint> path,
            @JsonProperty("service_id") String serviceId,
            List<Object> requests,
            @JsonProperty("group_service_id") String groupServiceId,
            @JsonProperty("group_requests") List<Object> groupRequests,
            @JsonProperty("payment_method") String paymentMethod,
            String remarks,
            List<AhamoveItem> items,
            @JsonProperty("package_detail") List<AhamovePackageDetail> packageDetail
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AhamovePathPoint(
            BigDecimal lat,
            BigDecimal lng,
            String address,
            String name,
            String mobile,
            Integer cod,
            @JsonProperty("tracking_number") String trackingNumber
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AhamoveItem(
            @JsonProperty("_id") String id,
            String name,
            long price,
            int num
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AhamovePackageDetail(
            BigDecimal weight
    ) {}

    record AhamoveCreateOrderResponse(
            @JsonProperty("order_id") String orderId,
            String status,
            @JsonProperty("shared_link") String sharedLink,
            AhamoveOrderSummary order
    ) {}

    record AhamoveOrderSummary(
            @JsonProperty("total_fee") BigDecimal totalFee,
            @JsonProperty("total_price") BigDecimal totalPrice,
            @JsonProperty("total_pay") BigDecimal totalPay
    ) {}

    record AhamoveErrorPayload(
            String code,
            String error,
            String description,
            String message
    ) {}
}
