package com.lumintra.backend.device;

import com.lumintra.backend.commerce.domain.model.FulfillmentDeviceSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.infrastructure.persistence.LedInstanceFulfillmentPortImpl;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.shared.exception.commerce.OrderFulfillmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedInstanceFulfillmentPortImplTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    private LedInstanceFulfillmentPortImpl port;

    @BeforeEach
    void setUp() {
        port = new LedInstanceFulfillmentPortImpl(
                ledInstanceRepository,
                ledModelRepository
        );
    }

    private LedInstanceEntity createInstance(Long id, Long modelId, String serial, String mac, LedInstanceStatus status) {
        return LedInstanceEntity.createWithState(
                modelId,
                serial,
                mac,
                null,
                null,
                status,
                Instant.now(),
                null,
                null
        );
    }

    @Test
    @DisplayName("COM-16 Port UTCID01 [P]: Successfully links unit with serial number and model match")
    void linkAndMarkInTransit_withSerialNumber_success() {
        String serialNumber = "LMT-001";
        LedInstanceEntity instance = createInstance(15L, 5L, "LMT-001", "AA:BB:CC:DD:EE:01", LedInstanceStatus.IN_STOCK);
        instance.setInstanceId(15L);

        LedModelEntity model = LedModelEntity.create(5L, "Matrix 64x64", java.util.Map.of(), 10L);

        when(ledInstanceRepository.findBySerialNumber("LMT-001")).thenReturn(Optional.of(instance));
        when(ledInstanceRepository.updateStatusIfCurrent(eq(15L), eq(LedInstanceStatus.IN_STOCK), eq(LedInstanceStatus.IN_TRANSIT), any(Instant.class)))
                .thenReturn(1);
        when(ledModelRepository.findById(5L)).thenReturn(Optional.of(model));

        FulfillmentDeviceSnapshot snapshot = port.linkAndMarkInTransit(serialNumber, 5L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.instanceId()).isEqualTo(15L);
        assertThat(snapshot.modelId()).isEqualTo(5L);
        assertThat(snapshot.modelName()).isEqualTo("Matrix 64x64");
        assertThat(snapshot.serialNumber()).isEqualTo("LMT-001");
        assertThat(snapshot.macAddress()).isEqualTo("AA:BB:CC:DD:EE:01");
        assertThat(snapshot.status()).isEqualTo("IN_TRANSIT");
    }

    @Test
    @DisplayName("COM-16 Port UTCID02 [P]: Successfully links unit when model lookup is empty")
    void linkAndMarkInTransit_modelLookupEmpty_fallbackModelName() {
        String serialNumber = "LMT-002";
        LedInstanceEntity instance = createInstance(16L, 5L, "LMT-002", "AA:BB:CC:DD:EE:02", LedInstanceStatus.IN_STOCK);
        instance.setInstanceId(16L);

        when(ledInstanceRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.of(instance));
        when(ledInstanceRepository.updateStatusIfCurrent(eq(16L), eq(LedInstanceStatus.IN_STOCK), eq(LedInstanceStatus.IN_TRANSIT), any(Instant.class)))
                .thenReturn(1);
        when(ledModelRepository.findById(5L)).thenReturn(Optional.empty());

        FulfillmentDeviceSnapshot snapshot = port.linkAndMarkInTransit(serialNumber, 5L);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.instanceId()).isEqualTo(16L);
        assertThat(snapshot.modelName()).isEqualTo("LED Model #5");
    }

    @Test
    @DisplayName("COM-16 Port UTCID03 [A]: Throws DEVICE_NOT_FOUND when serial number not found")
    void linkAndMarkInTransit_serialNotFound_throwsDeviceNotFound() {
        String serialNumber = "MISSING-SN";
        when(ledInstanceRepository.findBySerialNumber(serialNumber)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> port.linkAndMarkInTransit(serialNumber, 5L))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ofe.getErrorCode()).isEqualTo("DEVICE_NOT_FOUND");
                });
    }

    @Test
    @DisplayName("COM-16 Port UTCID04 [A]: Throws DEVICE_NOT_IN_STOCK when device is already BOUND or IN_TRANSIT")
    void linkAndMarkInTransit_deviceNotStock_throwsDeviceNotInStock() {
        LedInstanceEntity instance = createInstance(17L, 5L, "LMT-003", "AA:BB:CC:DD:EE:03", LedInstanceStatus.BOUND);
        instance.setInstanceId(17L);

        when(ledInstanceRepository.findBySerialNumber("LMT-003")).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> port.linkAndMarkInTransit("LMT-003", 5L))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ofe.getErrorCode()).isEqualTo("DEVICE_NOT_IN_STOCK");
                });
    }

    @Test
    @DisplayName("COM-16 Port UTCID05 [A]: Throws HARDWARE_MODEL_MISMATCH when device model does not match expected")
    void linkAndMarkInTransit_modelMismatch_throwsHardwareModelMismatch() {
        LedInstanceEntity instance = createInstance(18L, 6L, "LMT-004", "AA:BB:CC:DD:EE:04", LedInstanceStatus.IN_STOCK);
        instance.setInstanceId(18L);

        when(ledInstanceRepository.findBySerialNumber("LMT-004")).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> port.linkAndMarkInTransit("LMT-004", 5L))
                .isInstanceOf(OrderFulfillmentException.class)
                .satisfies(ex -> {
                    OrderFulfillmentException ofe = (OrderFulfillmentException) ex;
                    assertThat(ofe.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ofe.getErrorCode()).isEqualTo("HARDWARE_MODEL_MISMATCH");
                });
    }

    @Test
    @DisplayName("COM-16 Port UTCID06 [P]: releaseToStock successfully transitions unit back to IN_STOCK")
    void releaseToStock_success() {
        when(ledInstanceRepository.updateStatusIfCurrent(eq(15L), eq(LedInstanceStatus.IN_TRANSIT), eq(LedInstanceStatus.IN_STOCK), any(Instant.class)))
                .thenReturn(1);

        port.releaseToStock(15L);

        verify(ledInstanceRepository).updateStatusIfCurrent(eq(15L), eq(LedInstanceStatus.IN_TRANSIT), eq(LedInstanceStatus.IN_STOCK), any(Instant.class));
    }
}
