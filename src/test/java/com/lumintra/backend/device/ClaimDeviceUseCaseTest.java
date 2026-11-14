package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.ClaimDeviceRequest;
import com.lumintra.backend.device.api.response.ClaimDeviceResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.ClaimDeviceUseCase;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.VerifiedDeviceQrPayload;
import com.lumintra.backend.device.domain.service.FactoryQrPayloadService;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.shared.exception.device.DeviceAlreadyBoundException;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.InvalidDeviceQrException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.OrderNotDeliveredYetException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimDeviceUseCaseTest {

    private static final String TEST_SECRET = "claim-usecase-test-secret-2026";
    private static final Long CUSTOMER_ID = 42L;
    private static final Long INSTANCE_ID = 1001L;
    private static final Long MODEL_ID = 101L;
    private static final String SERIAL_NUMBER = "LMT-64X64-0005";
    private static final String MAC_ADDRESS = "AA:BB:CC:11:22:05";

    private final FactoryQrPayloadService qrService = new HmacFactoryQrPayloadService(TEST_SECRET);
    private final LedInstanceMapper ledInstanceMapper = new LedInstanceMapper(qrService, new LedModelMapper());

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    private ClaimDeviceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ClaimDeviceUseCase(
                qrService,
                ledInstanceRepository,
                ledModelRepository,
                orderFulfillmentLookupService,
                ledInstanceMapper
        );
    }

    @Test
    @DisplayName("DEV-93 UC-01 [N]: Claims IN_TRANSIT device on DELIVERED order with custom deviceName and activates 180-day warranty")
    void execute_validQrAndDeliveredOrderWithCustomName_bindsAndActivatesWarranty() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity instance = createUnboundInstance(LedInstanceStatus.IN_TRANSIT);
        LedModelEntity model = createModelEntity();

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(instance));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        INSTANCE_ID, 5005L, 9005L, CUSTOMER_ID, "DELIVERED")));
        when(ledModelRepository.findById(MODEL_ID)).thenReturn(Optional.of(model));
        when(ledInstanceRepository.bindIfUnbound(eq(INSTANCE_ID), eq(CUSTOMER_ID), eq("My Rear Window LED"), any(), any()))
                .thenReturn(1);

        Instant before = Instant.now();
        ClaimDeviceResponse response = useCase.execute(
                CUSTOMER_ID,
                new ClaimDeviceRequest(qrPayload, "  My Rear Window LED  ")
        );
        Instant after = Instant.now();

        assertThat(response.deviceId()).isEqualTo(INSTANCE_ID);
        assertThat(response.serialNumber()).isEqualTo(SERIAL_NUMBER);
        assertThat(response.macAddress()).isEqualTo(MAC_ADDRESS);
        assertThat(response.deviceName()).isEqualTo("My Rear Window LED");
        assertThat(response.modelId()).isEqualTo(MODEL_ID);
        assertThat(response.modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(response.spec().widthPx()).isEqualTo(64);
        assertThat(response.spec().heightPx()).isEqualTo(64);
        assertThat(response.spec().attributes()).containsExactly(
                new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C")
        );
        assertThat(response.boundAt()).isBetween(before, after);
        assertThat(response.warrantyExpiresAt())
                .isEqualTo(response.boundAt().plus(ClaimDeviceUseCase.WARRANTY_DURATION_DAYS, ChronoUnit.DAYS));

        assertThat(instance.getStatus()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(instance.getOwnerCustomerId()).isEqualTo(CUSTOMER_ID);
    }

    @Test
    @DisplayName("DEV-93 UC-02 [B]: Defaults deviceName to modelName when request.deviceName is null or blank on COMPLETED order")
    void execute_blankDeviceNameOnCompletedOrder_defaultsToModelName() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity instance = createUnboundInstance(LedInstanceStatus.IN_TRANSIT);
        LedModelEntity model = createModelEntity();

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(instance));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        INSTANCE_ID, 5007L, 9007L, CUSTOMER_ID, "COMPLETED")));
        when(ledModelRepository.findById(MODEL_ID)).thenReturn(Optional.of(model));
        when(ledInstanceRepository.bindIfUnbound(
                eq(INSTANCE_ID), eq(CUSTOMER_ID), eq("Lumintra Matrix 64x64 Pro"), any(), any()))
                .thenReturn(1);

        ClaimDeviceResponse response = useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, "   "));

        assertThat(response.deviceName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        ArgumentCaptor<Instant> boundCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> expiryCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(ledInstanceRepository).bindIfUnbound(
                eq(INSTANCE_ID),
                eq(CUSTOMER_ID),
                eq("Lumintra Matrix 64x64 Pro"),
                boundCaptor.capture(),
                expiryCaptor.capture()
        );
        assertThat(expiryCaptor.getValue()).isEqualTo(boundCaptor.getValue().plus(180, ChronoUnit.DAYS));
    }

    @Test
    @DisplayName("DEV-93 UC-03 [A]: Rejects tampered or malformed QR payload with InvalidDeviceQrException (400 INVALID_DEVICE_QR)")
    void execute_tamperedQrPayload_throwsInvalidDeviceQrException() {
        String tamperedQr = "{\"v\":1,\"sn\":\"LMT-64X64-0005\",\"mac\":\"AA:BB:CC:11:22:05\",\"sig\":\""
                + "0".repeat(64) + "\"}";

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(tamperedQr, null)))
                .isInstanceOf(InvalidDeviceQrException.class);

        verify(ledInstanceRepository, never()).findBySerialNumberAndMacAddress(any(), any());
    }

    @Test
    @DisplayName("DEV-93 UC-04 [A]: Rejects valid QR signature with no matching DB row with LedInstanceNotFoundException (404)")
    void execute_unknownInstance_throwsLedInstanceNotFoundException() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(LedInstanceNotFoundException.class);
    }

    @Test
    @DisplayName("DEV-93 UC-05 [A]: Rejects already BOUND device with DeviceAlreadyBoundException (409 DEVICE_ALREADY_BOUND)")
    void execute_alreadyBoundInstance_throwsDeviceAlreadyBoundException() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity boundInstance = createUnboundInstance(LedInstanceStatus.BOUND);

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(boundInstance));

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(DeviceAlreadyBoundException.class);

        verify(orderFulfillmentLookupService, never()).findFulfillmentByInstanceId(any());
        verify(ledInstanceRepository, never()).bindIfUnbound(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEV-93 UC-06 [A]: Rejects unfulfilled device or device owned by another customer with DeviceOwnershipMismatchException (403)")
    void execute_unfulfilledOrDifferentCustomerOrder_throwsDeviceOwnershipMismatchException() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity instance = createUnboundInstance(LedInstanceStatus.IN_STOCK);

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(instance));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(DeviceOwnershipMismatchException.class);

        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        INSTANCE_ID, 5005L, 9005L, 999L, "DELIVERED")));

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(DeviceOwnershipMismatchException.class);

        verify(ledInstanceRepository, never()).bindIfUnbound(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEV-93 UC-07 [A]: Rejects device on SHIPPING or PROCESSING order with OrderNotDeliveredYetException (409)")
    void execute_orderNotDeliveredYet_throwsOrderNotDeliveredYetException() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity instance = createUnboundInstance(LedInstanceStatus.IN_TRANSIT);

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(instance));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        INSTANCE_ID, 5004L, 9004L, CUSTOMER_ID, "SHIPPING")));

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(OrderNotDeliveredYetException.class);

        verify(ledInstanceRepository, never()).bindIfUnbound(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("DEV-93 UC-08 [B]: Rejects concurrent claim when atomic bindIfUnbound updates 0 rows with DeviceAlreadyBoundException (409)")
    void execute_concurrentBindUpdateZeroRows_throwsDeviceAlreadyBoundException() {
        String qrPayload = qrService.generateQrPayload(SERIAL_NUMBER, MAC_ADDRESS);
        LedInstanceEntity instance = createUnboundInstance(LedInstanceStatus.IN_TRANSIT);

        when(ledInstanceRepository.findBySerialNumberAndMacAddress(SERIAL_NUMBER, MAC_ADDRESS))
                .thenReturn(Optional.of(instance));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(INSTANCE_ID))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(
                        INSTANCE_ID, 5005L, 9005L, CUSTOMER_ID, "DELIVERED")));
        when(ledModelRepository.findById(MODEL_ID)).thenReturn(Optional.empty());
        when(ledInstanceRepository.bindIfUnbound(eq(INSTANCE_ID), eq(CUSTOMER_ID), eq(SERIAL_NUMBER), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> useCase.execute(CUSTOMER_ID, new ClaimDeviceRequest(qrPayload, null)))
                .isInstanceOf(DeviceAlreadyBoundException.class);
    }

    private LedInstanceEntity createUnboundInstance(LedInstanceStatus status) {
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                MODEL_ID,
                SERIAL_NUMBER,
                MAC_ADDRESS,
                "REV_1_0",
                null,
                null,
                status,
                Instant.parse("2026-10-15T08:00:00Z"),
                null,
                null
        );
        ReflectionTestUtils.setField(entity, "instanceId", INSTANCE_ID);
        return entity;
    }

    private LedModelEntity createModelEntity() {
        return LedModelEntity.createCatalogModel(
                MODEL_ID,
                MODEL_ID,
                "Lumintra Matrix 64x64 Pro",
                64,
                64,
                Map.of("attributes", List.of(Map.of("label", "Voltage", "value", "5V USB-C"))),
                50L,
                LedModelStatus.ACTIVE
        );
    }
}
