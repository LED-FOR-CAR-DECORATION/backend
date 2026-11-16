package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.CustomerDeviceDetailResponse;
import com.lumintra.backend.device.api.response.LedModelListResponse;
import com.lumintra.backend.device.application.GetCustomerDeviceDetailUseCase;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCustomerDeviceDetailUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    private GetCustomerDeviceDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        LedInstanceMapper mapper = new LedInstanceMapper(
                new HmacFactoryQrPayloadService("customer-detail-test-secret"),
                new LedModelMapper()
        );
        useCase = new GetCustomerDeviceDetailUseCase(
                ledInstanceRepository,
                ledModelRepository,
                orderFulfillmentLookupService,
                mapper
        );
    }

    @Test
    @DisplayName("DEV-95 UC-01 [N]: Returns full device profile, model specs, ACTIVE warranty, and fulfillment linkage for owned device")
    void execute_ownedBoundDevice_returnsDetailWithModelAndFulfillment() {
        Instant boundAt = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = Instant.now().plus(175, ChronoUnit.DAYS);

        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0001",
                "AA:BB:CC:11:22:01",
                "REV_1_0",
                42L,
                "Rear Window Matrix",
                LedInstanceStatus.BOUND,
                boundAt.minus(30, ChronoUnit.DAYS),
                boundAt,
                warrantyExpiresAt
        );
        ReflectionTestUtils.setField(entity, "instanceId", 2001L);
        entity.setCurrentFirmwareId(12L);

        LedModelEntity modelEntity = LedModelEntity.createCatalogModel(
                101L,
                301L,
                "Lumintra Matrix 64x64 Pro",
                64,
                64,
                Map.of("attributes", List.of(
                        Map.of("label", "Voltage", "value", "5V USB-C"),
                        Map.of("label", "IP Rating", "value", "IP65")
                )),
                20L,
                LedModelStatus.ACTIVE
        );

        when(ledInstanceRepository.findById(2001L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(101L)).thenReturn(Optional.of(modelEntity));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(2001L))
                .thenReturn(Optional.of(new InstanceFulfillmentSnapshot(2001L, 55L, 102L)));

        CustomerDeviceDetailResponse result = useCase.execute(42L, 2001L);

        assertThat(result.deviceId()).isEqualTo(2001L);
        assertThat(result.serialNumber()).isEqualTo("LMT-64X64-0001");
        assertThat(result.macAddress()).isEqualTo("AA:BB:CC:11:22:01");
        assertThat(result.deviceName()).isEqualTo("Rear Window Matrix");
        assertThat(result.status()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(result.currentFirmwareId()).isEqualTo(12L);
        assertThat(result.boundAt()).isEqualTo(boundAt);
        assertThat(result.warrantyExpiresAt()).isEqualTo(warrantyExpiresAt);
        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(result.model()).isEqualTo(CustomerDeviceDetailResponse.ModelInfo.builder()
                .modelId(101L)
                .productId(301L)
                .modelName("Lumintra Matrix 64x64 Pro")
                .status(LedModelStatus.ACTIVE)
                .spec(LedModelListResponse.LedSpec.builder()
                        .widthPx(64)
                        .heightPx(64)
                        .attributes(List.of(
                                new LedModelListResponse.SpecAttribute("Voltage", "5V USB-C"),
                                new LedModelListResponse.SpecAttribute("IP Rating", "IP65")
                        ))
                        .build())
                .build());
        assertThat(result.fulfillment()).isEqualTo(new CustomerDeviceDetailResponse.FulfillmentInfo(55L, 102L));

        verify(ledInstanceRepository).findById(2001L);
        verify(ledModelRepository).findById(101L);
        verify(orderFulfillmentLookupService).findFulfillmentByInstanceId(2001L);
    }

    @Test
    @DisplayName("DEV-95 UC-02 [B]: Allows DEPRECATED model metadata, EXPIRED warranty, null firmwareId, and missing fulfillment")
    void execute_deprecatedModelAndExpiredWarrantyWithoutFulfillment_returnsDetail() {
        Instant boundAt = Instant.now().minus(200, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = Instant.now().minus(20, ChronoUnit.DAYS);

        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                102L,
                "LMT-32X16-0002",
                "AA:BB:CC:11:22:02",
                "REV_1_0",
                42L,
                null,
                LedInstanceStatus.BOUND,
                boundAt.minus(10, ChronoUnit.DAYS),
                boundAt,
                warrantyExpiresAt
        );
        ReflectionTestUtils.setField(entity, "instanceId", 2002L);

        LedModelEntity deprecatedModel = LedModelEntity.createCatalogModel(
                102L,
                null,
                "Lumintra Legacy 32x16",
                32,
                16,
                Map.of(),
                null,
                LedModelStatus.DEPRECATED
        );

        when(ledInstanceRepository.findById(2002L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(102L)).thenReturn(Optional.of(deprecatedModel));
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(2002L)).thenReturn(Optional.empty());

        CustomerDeviceDetailResponse result = useCase.execute(42L, 2002L);

        assertThat(result.deviceId()).isEqualTo(2002L);
        assertThat(result.currentFirmwareId()).isNull();
        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.EXPIRED);
        assertThat(result.model().modelId()).isEqualTo(102L);
        assertThat(result.model().productId()).isNull();
        assertThat(result.model().status()).isEqualTo(LedModelStatus.DEPRECATED);
        assertThat(result.fulfillment()).isNull();
    }

    @Test
    @DisplayName("DEV-95 UC-03 [B]: Falls back cleanly when model row is missing and warranty timestamps are unactivated")
    void execute_missingModelRowAndUnactivatedWarranty_returnsFallbackDetail() {
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                103L,
                "LMT-64X32-0003",
                "AA:BB:CC:11:22:03",
                "REV_1_0",
                42L,
                "Cabin Bar",
                LedInstanceStatus.BOUND,
                Instant.now().minus(5, ChronoUnit.DAYS),
                null,
                null
        );
        ReflectionTestUtils.setField(entity, "instanceId", 2003L);

        when(ledInstanceRepository.findById(2003L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(103L)).thenReturn(Optional.empty());
        when(orderFulfillmentLookupService.findFulfillmentByInstanceId(2003L)).thenReturn(Optional.empty());

        CustomerDeviceDetailResponse result = useCase.execute(42L, 2003L);

        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.NOT_ACTIVATED);
        assertThat(result.model()).isEqualTo(CustomerDeviceDetailResponse.ModelInfo.builder().modelId(103L).build());
        assertThat(result.fulfillment()).isNull();
    }

    @Test
    @DisplayName("DEV-95 UC-04 [A]: Throws LedInstanceNotFoundException when deviceId does not exist or is null")
    void execute_missingOrNullDeviceId_throwsNotFound() {
        when(ledInstanceRepository.findById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(42L, 9999L))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessage("LED hardware device not found.");

        assertThatThrownBy(() -> useCase.execute(42L, null))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessage("LED hardware device not found.");

        verifyNoInteractions(ledModelRepository, orderFulfillmentLookupService);
    }

    @Test
    @DisplayName("DEV-95 UC-05 [A]: Throws DeviceOwnershipMismatchException when device is owned by another customer or unbound")
    void execute_otherOwnerOrUnboundDevice_throwsOwnershipMismatch() {
        LedInstanceEntity ownedByOther = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0004",
                "AA:BB:CC:11:22:04",
                "REV_1_0",
                99L,
                "Other Car",
                LedInstanceStatus.BOUND,
                Instant.now().minus(10, ChronoUnit.DAYS),
                Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().plus(175, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(ownedByOther, "instanceId", 2004L);

        LedInstanceEntity unbound = LedInstanceEntity.createInStock(
                101L,
                "LMT-64X64-0005",
                "AA:BB:CC:11:22:05",
                "REV_1_0",
                Instant.now().minus(10, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(unbound, "instanceId", 2005L);

        when(ledInstanceRepository.findById(2004L)).thenReturn(Optional.of(ownedByOther));
        when(ledInstanceRepository.findById(2005L)).thenReturn(Optional.of(unbound));

        assertThatThrownBy(() -> useCase.execute(42L, 2004L))
                .isInstanceOf(DeviceOwnershipMismatchException.class)
                .hasMessage("You do not own this LED hardware device.");

        assertThatThrownBy(() -> useCase.execute(42L, 2005L))
                .isInstanceOf(DeviceOwnershipMismatchException.class)
                .hasMessage("You do not own this LED hardware device.");

        verifyNoInteractions(ledModelRepository, orderFulfillmentLookupService);
    }

    @Test
    @DisplayName("DEV-95 UC-06 [A]: Throws InsufficientAuthenticationException when customerId is null")
    void execute_nullCustomerId_throwsInsufficientAuthentication() {
        assertThatThrownBy(() -> useCase.execute(null, 2001L))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessage("Authenticated customer identity is required.");

        verifyNoInteractions(ledInstanceRepository, ledModelRepository, orderFulfillmentLookupService);
    }
}
