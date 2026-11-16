package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.CustomerDeviceFilterRequest;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse;
import com.lumintra.backend.device.api.response.CustomerDeviceListResponse.CustomerDeviceItem;
import com.lumintra.backend.device.application.ListCustomerDevicesUseCase;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelStatus;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedModelEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedModelMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListCustomerDevicesUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    private ListCustomerDevicesUseCase useCase;

    @BeforeEach
    void setUp() {
        LedInstanceMapper ledInstanceMapper = new LedInstanceMapper(null, new LedModelMapper());
        useCase = new ListCustomerDevicesUseCase(ledInstanceRepository, ledModelRepository, ledInstanceMapper);
    }

    @Test
    @DisplayName("DEV-94 UC-01 [N]: Returns paginated customer owned devices with model metadata including DEPRECATED models")
    void execute_shouldReturnOwnedDevicesWithActiveAndDeprecatedModelsAndPaginationMeta() {
        Long customerId = 9001L;
        Instant now = Instant.now();
        Instant manufacturedAt = now.minus(450, ChronoUnit.DAYS);
        Instant newerBoundAt = now.minus(1, ChronoUnit.DAYS);
        Instant olderBoundAt = now.minus(400, ChronoUnit.DAYS);

        LedInstanceEntity newerDevice = LedInstanceEntity.createWithState(
                101L,
                "SN-2026-0002",
                "aa:bb:cc:dd:ee:02",
                "REV_1_0",
                customerId,
                "Rear Window Matrix",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                newerBoundAt,
                now.plus(364, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(newerDevice, "instanceId", 2002L);
        newerDevice.setCurrentFirmwareId(3L);

        LedInstanceEntity olderDeviceWithDeprecatedModel = LedInstanceEntity.createWithState(
                102L,
                "SN-2026-0001",
                "aa:bb:cc:dd:ee:01",
                "REV_1_0",
                customerId,
                "Side Panel Classic",
                LedInstanceStatus.BOUND,
                manufacturedAt,
                olderBoundAt,
                now.minus(35, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(olderDeviceWithDeprecatedModel, "instanceId", 2001L);

        LedModelEntity activeModel = LedModelEntity.createCatalogModel(
                101L,
                101L,
                "Lumintra Neo 64x16",
                64,
                16,
                Map.of(),
                25L,
                LedModelStatus.ACTIVE
        );

        LedModelEntity deprecatedModel = LedModelEntity.createCatalogModel(
                102L,
                102L,
                "Lumintra Classic 32x16",
                32,
                16,
                Map.of(),
                0L,
                LedModelStatus.DEPRECATED
        );

        Pageable expectedPageable = PageRequest.of(1, 2);
        Page<LedInstanceEntity> pageResult = new PageImpl<>(
                List.of(newerDevice, olderDeviceWithDeprecatedModel),
                expectedPageable,
                5L
        );

        when(ledInstanceRepository.findAllByOwnerCustomerIdOrderByBoundAtDesc(customerId, expectedPageable))
                .thenReturn(pageResult);
        when(ledModelRepository.findAllById(Set.of(101L, 102L)))
                .thenReturn(List.of(activeModel, deprecatedModel));

        CustomerDeviceListResponse response = useCase.execute(customerId, new CustomerDeviceFilterRequest(1, 2));

        assertThat(response.items()).hasSize(2);
        assertThat(response.meta().getPage()).isEqualTo(1);
        assertThat(response.meta().getSize()).isEqualTo(2);
        assertThat(response.meta().getTotalElements()).isEqualTo(5L);
        assertThat(response.meta().getTotalPages()).isEqualTo(3);
        assertThat(response.meta().isHasNext()).isTrue();
        assertThat(response.meta().isHasPrevious()).isTrue();

        CustomerDeviceItem first = response.items().get(0);
        assertThat(first.deviceId()).isEqualTo(2002L);
        assertThat(first.serialNumber()).isEqualTo("SN-2026-0002");
        assertThat(first.macAddress()).isEqualTo("AA:BB:CC:DD:EE:02");
        assertThat(first.deviceName()).isEqualTo("Rear Window Matrix");
        assertThat(first.status()).isEqualTo(LedInstanceStatus.BOUND);
        assertThat(first.modelId()).isEqualTo(101L);
        assertThat(first.modelName()).isEqualTo("Lumintra Neo 64x16");
        assertThat(first.widthPx()).isEqualTo(64);
        assertThat(first.heightPx()).isEqualTo(16);
        assertThat(first.currentFirmwareId()).isEqualTo(3L);
        assertThat(first.boundAt()).isEqualTo(newerBoundAt);
        assertThat(first.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);

        CustomerDeviceItem second = response.items().get(1);
        assertThat(second.deviceId()).isEqualTo(2001L);
        assertThat(second.modelId()).isEqualTo(102L);
        assertThat(second.modelName()).isEqualTo("Lumintra Classic 32x16");
        assertThat(second.widthPx()).isEqualTo(32);
        assertThat(second.heightPx()).isEqualTo(16);
        assertThat(second.currentFirmwareId()).isNull();
        assertThat(second.boundAt()).isEqualTo(olderBoundAt);
        assertThat(second.warrantyStatus()).isEqualTo(WarrantyStatus.EXPIRED);
    }

    @Test
    @DisplayName("DEV-94 UC-02 [B]: Defaults null filter to page 0, size 20 and skips model lookup when page is empty")
    void execute_shouldDefaultPaginationAndReturnEmptyItemsWhenCustomerHasNoDevices() {
        Long customerId = 9002L;
        Pageable defaultPageable = PageRequest.of(0, 20);
        when(ledInstanceRepository.findAllByOwnerCustomerIdOrderByBoundAtDesc(customerId, defaultPageable))
                .thenReturn(Page.empty(defaultPageable));

        CustomerDeviceListResponse response = useCase.execute(customerId, null);

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getPage()).isZero();
        assertThat(response.meta().getSize()).isEqualTo(20);
        assertThat(response.meta().getTotalElements()).isZero();
        assertThat(response.meta().getTotalPages()).isZero();
        assertThat(response.meta().isHasNext()).isFalse();
        assertThat(response.meta().isHasPrevious()).isFalse();
        verify(ledInstanceRepository).findAllByOwnerCustomerIdOrderByBoundAtDesc(customerId, defaultPageable);
        verify(ledModelRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("DEV-94 UC-03 [B]: Gracefully handles device whose hardware model record is missing")
    void execute_shouldFallbackGracefullyWhenModelRecordIsMissing() {
        Long customerId = 9003L;
        Instant now = Instant.now();

        LedInstanceEntity device = LedInstanceEntity.createWithState(
                999L,
                "SN-2026-0003",
                "AA:BB:CC:DD:EE:03",
                "REV_1_0",
                customerId,
                "Orphaned Model Device",
                LedInstanceStatus.BOUND,
                now.minus(10, ChronoUnit.DAYS),
                now.minus(2, ChronoUnit.HOURS),
                now.plus(365, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(device, "instanceId", 2003L);

        Pageable pageable = PageRequest.of(0, 20);
        when(ledInstanceRepository.findAllByOwnerCustomerIdOrderByBoundAtDesc(customerId, pageable))
                .thenReturn(new PageImpl<>(List.of(device), pageable, 1L));
        when(ledModelRepository.findAllById(Set.of(999L)))
                .thenReturn(List.of());

        CustomerDeviceListResponse response = useCase.execute(customerId, new CustomerDeviceFilterRequest());

        assertThat(response.items()).hasSize(1);
        CustomerDeviceItem item = response.items().get(0);
        assertThat(item.deviceId()).isEqualTo(2003L);
        assertThat(item.modelId()).isEqualTo(999L);
        assertThat(item.modelName()).isNull();
        assertThat(item.widthPx()).isNull();
        assertThat(item.heightPx()).isNull();
        assertThat(item.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(response.meta().getTotalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("DEV-94 UC-04 [A]: Rejects null customerId with InsufficientAuthenticationException")
    void execute_shouldThrowInsufficientAuthenticationWhenCustomerIdIsNull() {
        assertThatThrownBy(() -> useCase.execute(null, new CustomerDeviceFilterRequest()))
                .isInstanceOf(InsufficientAuthenticationException.class);

        verify(ledInstanceRepository, never()).findAllByOwnerCustomerIdOrderByBoundAtDesc(any(), any(Pageable.class));
    }
}
