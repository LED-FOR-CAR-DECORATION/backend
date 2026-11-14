package com.lumintra.backend.commerce;

import com.lumintra.backend.commerce.api.request.AhamoveWebhookRequest;
import com.lumintra.backend.commerce.api.response.AhamoveWebhookResponse;
import com.lumintra.backend.commerce.application.ProcessAhamoveWebhookUseCase;
import com.lumintra.backend.commerce.domain.model.DeliveryStatus;
import com.lumintra.backend.commerce.domain.model.OrderStatus;
import com.lumintra.backend.commerce.domain.model.OrderType;
import com.lumintra.backend.commerce.domain.model.ResolvedShippingAddress;
import com.lumintra.backend.commerce.infrastructure.integration.AhamoveProperties;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderDeliveryEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.entity.OrderEntity;
import com.lumintra.backend.commerce.infrastructure.persistence.mapper.OrderMapper;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderDeliveryRepository;
import com.lumintra.backend.commerce.infrastructure.persistence.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessAhamoveWebhookUseCaseTest {

    private static final String WEBHOOK_TOKEN = "aha-webhook-secret-2026";

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private OrderRepository orderRepository;

    private final OrderMapper orderMapper = new OrderMapper();

    private ProcessAhamoveWebhookUseCase useCase;

    @EnableConfigurationProperties(AhamoveProperties.class)
    static class PropertiesConfig {}

    @BeforeEach
    void setUp() {
        useCase = new ProcessAhamoveWebhookUseCase(
                orderDeliveryRepository,
                orderRepository,
                orderMapper,
                defaultProperties("X-Api-Key", WEBHOOK_TOKEN)
        );
    }

    private AhamoveProperties defaultProperties(String headerName, String token) {
        return new AhamoveProperties(
                "https://partner-apistg.ahamove.com/v3",
                "aha-api-key",
                "84901112222",
                "BALANCE",
                null,
                new AhamoveProperties.Webhook(headerName, token)
        );
    }

    private OrderDeliveryEntity deliveryWithStatuses(
            Long orderId,
            Long deliveryId,
            String trackingNumber,
            OrderStatus orderStatus,
            DeliveryStatus deliveryStatus) {
        OrderEntity order = OrderEntity.of(
                orderId,
                15L,
                10L,
                new BigDecimal("250000.00"),
                BigDecimal.ZERO,
                new BigDecimal("250000.00"),
                OrderType.PHYSICAL,
                orderStatus,
                Instant.parse("2026-11-12T12:00:00Z")
        );
        OrderDeliveryEntity delivery = OrderDeliveryEntity.createPending(
                order,
                new ResolvedShippingAddress(
                        10L,
                        "Dang Phuoc Nguyen",
                        "0912345678",
                        "475A Dien Bien Phu, Binh Thanh, HCMC",
                        new BigDecimal("10.8018493"),
                        new BigDecimal("106.7144660")
                )
        );
        ReflectionTestUtils.setField(delivery, "deliveryId", deliveryId);
        delivery.setTrackingNumber(trackingNumber);
        delivery.setDeliveryStatus(deliveryStatus);
        return delivery;
    }

    @Test
    @DisplayName("COM-14 UTCID01 [N]: Authenticates API-Key and Bearer headers, normalizes multi-drop _id, and maps ASSIGNING/ACCEPTED -> ASSIGNED + SHIPPING")
    void execute_authenticatesAndMapsAssigningAcceptedAndMultiDropId() {
        OrderDeliveryEntity delivery = deliveryWithStatuses(
                102L,
                501L,
                "26ABCDEF",
                OrderStatus.PROCESSING,
                DeliveryStatus.PENDING
        );

        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26ABCDEF-1"))
                .thenReturn(Optional.empty());
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26ABCDEF"))
                .thenReturn(Optional.of(delivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(501L),
                eq(Set.of(DeliveryStatus.PENDING)),
                eq(DeliveryStatus.ASSIGNED),
                eq("https://expressstg.ahamove.com/s/26ABCDEF"),
                eq(new BigDecimal("33000")),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(102L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED)),
                eq(OrderStatus.SHIPPING),
                any(Instant.class)
        )).thenReturn(1);

        HttpHeaders apiKeyHeaders = new HttpHeaders();
        apiKeyHeaders.add("X-Api-Key", WEBHOOK_TOKEN);

        AhamoveWebhookRequest assigningRequest = new AhamoveWebhookRequest(
                "26ABCDEF-1",
                "ASSIGNING",
                null,
                "https://expressstg.ahamove.com/s/26ABCDEF",
                new BigDecimal("33000"),
                null,
                BigDecimal.ZERO,
                null
        );

        AhamoveWebhookResponse response = useCase.execute(apiKeyHeaders, assigningRequest);

        assertThat(response.outcome()).isEqualTo("PROCESSED");
        assertThat(response.trackingNumber()).isEqualTo("26ABCDEF");
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(response.orderStatus()).isEqualTo(OrderStatus.SHIPPING);

        HttpHeaders bearerHeaders = new HttpHeaders();
        bearerHeaders.setBearerAuth(WEBHOOK_TOKEN);
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26ABCDEF"))
                .thenReturn(Optional.of(delivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(501L),
                eq(Set.of(DeliveryStatus.PENDING)),
                eq(DeliveryStatus.ASSIGNED),
                eq(null),
                eq(new BigDecimal("33000")),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);

        AhamoveWebhookRequest acceptedRequest = new AhamoveWebhookRequest(
                "26ABCDEF",
                "ACCEPTED",
                null,
                null,
                null,
                new BigDecimal("33000"),
                null,
                null
        );
        AhamoveWebhookResponse acceptedResponse = useCase.execute(bearerHeaders, acceptedRequest);
        assertThat(acceptedResponse.outcome()).isEqualTo("PROCESSED");
        assertThat(acceptedResponse.deliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
    }

    @Test
    @DisplayName("COM-14 UTCID02 [N]: Maps IN PROCESS -> SHIPPING and COMPLETED -> DELIVERED with deliveredAt from complete_time")
    void execute_mapsInProcessAndCompletedDelivery() {
        OrderDeliveryEntity assignedDelivery = deliveryWithStatuses(
                102L, 501L, "26AHV100", OrderStatus.SHIPPING, DeliveryStatus.ASSIGNED
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26AHV100"))
                .thenReturn(Optional.of(assignedDelivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(501L),
                eq(Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP)),
                eq(DeliveryStatus.SHIPPING),
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(102L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED)),
                eq(OrderStatus.SHIPPING),
                any(Instant.class)
        )).thenReturn(0);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", "Apikey " + WEBHOOK_TOKEN);

        AhamoveWebhookResponse inProcessResponse = useCase.execute(
                headers,
                new AhamoveWebhookRequest("26AHV100", "IN PROCESS", null, null, null, null, null, null)
        );
        assertThat(inProcessResponse.outcome()).isEqualTo("PROCESSED");
        assertThat(inProcessResponse.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(inProcessResponse.orderStatus()).isEqualTo(OrderStatus.SHIPPING);

        OrderDeliveryEntity shippingDelivery = deliveryWithStatuses(
                102L, 501L, "26AHV100", OrderStatus.SHIPPING, DeliveryStatus.SHIPPING
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26AHV100"))
                .thenReturn(Optional.of(shippingDelivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(501L),
                eq(Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING)),
                eq(DeliveryStatus.DELIVERED),
                eq(null),
                eq(new BigDecimal("35000")),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(102L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.SHIPPING)),
                eq(OrderStatus.DELIVERED),
                any(Instant.class)
        )).thenReturn(1);

        AhamoveWebhookResponse completedResponse = useCase.execute(
                headers,
                new AhamoveWebhookRequest(
                        "26AHV100",
                        "COMPLETED",
                        null,
                        null,
                        new BigDecimal("35000"),
                        null,
                        new BigDecimal("1762938000"),
                        List.of(
                                new AhamoveWebhookRequest.PathPoint("COMPLETED"),
                                new AhamoveWebhookRequest.PathPoint("COMPLETED")
                        )
                )
        );

        assertThat(completedResponse.outcome()).isEqualTo("PROCESSED");
        assertThat(completedResponse.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(completedResponse.orderStatus()).isEqualTo(OrderStatus.DELIVERED);

        ArgumentCaptor<Instant> deliveredAtCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderDeliveryRepository).updateWebhookDeliveryStatusIfAllowed(
                eq(501L),
                any(),
                eq(DeliveryStatus.DELIVERED),
                eq(null),
                eq(new BigDecimal("35000")),
                deliveredAtCaptor.capture(),
                any(Instant.class)
        );
        assertThat(deliveredAtCaptor.getValue()).isEqualTo(Instant.ofEpochSecond(1762938000L));
    }

    @Test
    @DisplayName("COM-14 UTCID03 [N]: Maps CANCELLED, COMPLETED with failed drop-off path, and sub_status RETURNED to FAILED/RETURNED + DELIVERY_FAILED")
    void execute_mapsCancelledFailedDropoffAndReturned() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", WEBHOOK_TOKEN);

        OrderDeliveryEntity shippingDelivery = deliveryWithStatuses(
                103L, 502L, "26FAIL01", OrderStatus.SHIPPING, DeliveryStatus.SHIPPING
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26FAIL01"))
                .thenReturn(Optional.of(shippingDelivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(502L),
                eq(Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING)),
                eq(DeliveryStatus.FAILED),
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(103L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.SHIPPING)),
                eq(OrderStatus.DELIVERY_FAILED),
                any(Instant.class)
        )).thenReturn(1);

        AhamoveWebhookResponse completedWithFailedStop = useCase.execute(
                headers,
                new AhamoveWebhookRequest(
                        "26FAIL01",
                        "COMPLETED",
                        null,
                        null,
                        null,
                        null,
                        new BigDecimal("1762938100"),
                        List.of(
                                new AhamoveWebhookRequest.PathPoint("COMPLETED"),
                                new AhamoveWebhookRequest.PathPoint("FAILED")
                        )
                )
        );
        assertThat(completedWithFailedStop.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(completedWithFailedStop.orderStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);

        OrderDeliveryEntity failedDelivery = deliveryWithStatuses(
                104L, 503L, "26RET01", OrderStatus.DELIVERY_FAILED, DeliveryStatus.FAILED
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26RET01"))
                .thenReturn(Optional.of(failedDelivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(503L),
                eq(Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP, DeliveryStatus.SHIPPING, DeliveryStatus.FAILED)),
                eq(DeliveryStatus.RETURNED),
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(104L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED, OrderStatus.SHIPPING)),
                eq(OrderStatus.DELIVERY_FAILED),
                any(Instant.class)
        )).thenReturn(0);

        AhamoveWebhookResponse returnedResponse = useCase.execute(
                headers,
                new AhamoveWebhookRequest("26RET01", "COMPLETED", "RETURNED", null, null, null, null, null)
        );
        assertThat(returnedResponse.outcome()).isEqualTo("PROCESSED");
        assertThat(returnedResponse.deliveryStatus()).isEqualTo(DeliveryStatus.RETURNED);
        assertThat(returnedResponse.orderStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
    }

    @Test
    @DisplayName("COM-14 UTCID04 [A]: Missing, invalid, or unconfigured webhook token throws BadCredentialsException before DB lookup")
    void execute_invalidOrMissingCredentials_throwsBadCredentials() {
        AhamoveWebhookRequest request = new AhamoveWebhookRequest("26AHV100", "ACCEPTED", null, null, null, null, null, null);

        assertThatThrownBy(() -> useCase.execute(new HttpHeaders(), request))
                .isInstanceOf(BadCredentialsException.class);

        HttpHeaders wrongHeaders = new HttpHeaders();
        wrongHeaders.add("X-Api-Key", "wrong-secret");
        assertThatThrownBy(() -> useCase.execute(wrongHeaders, request))
                .isInstanceOf(BadCredentialsException.class);

        ProcessAhamoveWebhookUseCase unconfiguredUseCase = new ProcessAhamoveWebhookUseCase(
                orderDeliveryRepository,
                orderRepository,
                orderMapper,
                defaultProperties("X-Api-Key", "   ")
        );
        HttpHeaders validHeaders = new HttpHeaders();
        validHeaders.add("X-Api-Key", WEBHOOK_TOKEN);
        assertThatThrownBy(() -> unconfiguredUseCase.execute(validHeaders, request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(orderDeliveryRepository, orderRepository);
    }

    @Test
    @DisplayName("COM-14 UTCID05 [B]: Unmatched trackingNumber or unknown status returns 200 outcome UNMATCHED/IGNORED without failing")
    void execute_unmatchedOrUnknownStatus_returnsOkWithoutErrors() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", WEBHOOK_TOKEN);

        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26MISSING-1"))
                .thenReturn(Optional.empty());
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26MISSING"))
                .thenReturn(Optional.empty());

        AhamoveWebhookResponse unmatched = useCase.execute(
                headers,
                new AhamoveWebhookRequest("26MISSING-1", "ACCEPTED", null, null, null, null, null, null)
        );
        assertThat(unmatched.outcome()).isEqualTo("UNMATCHED");
        assertThat(unmatched.trackingNumber()).isEqualTo("26MISSING");
        assertThat(unmatched.deliveryStatus()).isNull();
        assertThat(unmatched.orderStatus()).isNull();
        verifyNoInteractions(orderRepository);

        OrderDeliveryEntity delivery = deliveryWithStatuses(
                105L, 504L, "26AHV105", OrderStatus.SHIPPING, DeliveryStatus.ASSIGNED
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26AHV105"))
                .thenReturn(Optional.of(delivery));

        AhamoveWebhookResponse unknownStatus = useCase.execute(
                headers,
                new AhamoveWebhookRequest("26AHV105", "REASSIGN", null, null, null, null, null, null)
        );
        assertThat(unknownStatus.outcome()).isEqualTo("IGNORED");
        assertThat(unknownStatus.deliveryStatus()).isEqualTo(DeliveryStatus.ASSIGNED);
        assertThat(unknownStatus.orderStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("COM-14 UTCID06 [A]: Terminal orders and out-of-order/duplicate transitions are idempotently ignored without overwriting state")
    void execute_terminalOrdersAndOutOfOrderEvents_areIgnored() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Api-Key", WEBHOOK_TOKEN);

        for (OrderStatus terminalStatus : List.of(
                OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.CANCELED, OrderStatus.REFUNDED)) {
            OrderDeliveryEntity terminalDelivery = deliveryWithStatuses(
                    200L, 600L, "26TERM", terminalStatus, DeliveryStatus.DELIVERED
            );
            when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26TERM"))
                    .thenReturn(Optional.of(terminalDelivery));

            AhamoveWebhookResponse response = useCase.execute(
                    headers,
                    new AhamoveWebhookRequest("26TERM", "CANCELLED", null, null, null, null, null, null)
            );
            assertThat(response.outcome()).isEqualTo("IGNORED");
            assertThat(response.orderStatus()).isEqualTo(terminalStatus);
            assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        }
        verifyNoInteractions(orderRepository);

        OrderDeliveryEntity alreadyShipping = deliveryWithStatuses(
                201L, 601L, "26SHIP", OrderStatus.SHIPPING, DeliveryStatus.SHIPPING
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26SHIP"))
                .thenReturn(Optional.of(alreadyShipping));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(601L), any(), eq(DeliveryStatus.ASSIGNED), any(), any(), any(), any(Instant.class)
        )).thenReturn(0);
        when(orderRepository.updateStatusIfIn(
                eq(201L), any(), eq(OrderStatus.SHIPPING), any(Instant.class)
        )).thenReturn(0);

        AhamoveWebhookResponse regression = useCase.execute(
                headers,
                new AhamoveWebhookRequest("26SHIP", "ASSIGNING", null, null, null, null, null, null)
        );
        assertThat(regression.outcome()).isEqualTo("IGNORED");
        assertThat(regression.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
        assertThat(regression.orderStatus()).isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    @DisplayName("COM-14: Allows unauthenticated webhook callbacks only when requireAuth is explicitly disabled for staging testing")
    void execute_whenRequireAuthDisabled_processesWithoutHeaders() {
        AhamoveProperties bypassProps = new AhamoveProperties(
                "https://partner-apistg.ahamove.com/v3",
                "aha-api-key",
                "84901112222",
                "BALANCE",
                null,
                new AhamoveProperties.Webhook(false, "X-Api-Key", "")
        );
        ProcessAhamoveWebhookUseCase bypassUseCase = new ProcessAhamoveWebhookUseCase(
                orderDeliveryRepository,
                orderRepository,
                orderMapper,
                bypassProps
        );

        OrderDeliveryEntity delivery = deliveryWithStatuses(
                106L, 505L, "26STG01", OrderStatus.SHIPPING, DeliveryStatus.ASSIGNED
        );
        when(orderDeliveryRepository.findFirstByTrackingNumberOrderByDeliveryIdDesc("26STG01"))
                .thenReturn(Optional.of(delivery));
        when(orderDeliveryRepository.updateWebhookDeliveryStatusIfAllowed(
                eq(505L),
                eq(Set.of(DeliveryStatus.PENDING, DeliveryStatus.ASSIGNED, DeliveryStatus.PICKED_UP)),
                eq(DeliveryStatus.SHIPPING),
                eq(null),
                eq(null),
                eq(null),
                any(Instant.class)
        )).thenReturn(1);
        when(orderRepository.updateStatusIfIn(
                eq(106L),
                eq(Set.of(OrderStatus.PROCESSING, OrderStatus.PARTIALLY_FULFILLED)),
                eq(OrderStatus.SHIPPING),
                any(Instant.class)
        )).thenReturn(0);

        AhamoveWebhookResponse response = bypassUseCase.execute(
                new HttpHeaders(),
                new AhamoveWebhookRequest("26STG01", "IN PROCESS", null, null, null, null, null, null)
        );

        assertThat(response.outcome()).isEqualTo("PROCESSED");
        assertThat(response.deliveryStatus()).isEqualTo(DeliveryStatus.SHIPPING);
    }

    @Test
    @DisplayName("COM-14: Spring Boot ConfigurationProperties binds webhook settings cleanly and preserves 5-arg constructor")
    void propertiesBinding_supportsWebhookSettingsAndFiveArgConstructor() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues(
                        "app.ahamove.base-url=https://partner-apistg.ahamove.com/v3",
                        "app.ahamove.webhook.require-auth=false",
                        "app.ahamove.webhook.header-name=X-AhaMove-Signature",
                        "app.ahamove.webhook.token=my-secret"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AhamoveProperties props = context.getBean(AhamoveProperties.class);
                    assertThat(props.isWebhookAuthRequired()).isFalse();
                    assertThat(props.normalizedWebhookHeaderName()).isEqualTo("X-AhaMove-Signature");
                    assertThat(props.resolvedWebhookToken()).isEqualTo("my-secret");
                });

        AhamoveProperties legacyConstructed = new AhamoveProperties(
                "https://partner-apistg.ahamove.com/v3",
                "key",
                "84901112222",
                "BALANCE",
                null
        );
        assertThat(legacyConstructed.isWebhookAuthRequired()).isTrue();
        assertThat(legacyConstructed.normalizedWebhookHeaderName()).isEqualTo("X-Api-Key");
        assertThat(legacyConstructed.resolvedWebhookToken()).isNull();
    }
}
