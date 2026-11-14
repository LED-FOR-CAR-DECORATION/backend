package com.lumintra.backend.device;

import com.lumintra.backend.device.api.response.LedInstanceDetailResponse;
import com.lumintra.backend.device.application.GetLedInstanceDetailUseCase;
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
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetLedInstanceDetailUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelRepository ledModelRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    private HmacFactoryQrPayloadService qrService;
    private GetLedInstanceDetailUseCase useCase;

    @BeforeEach
    void setUp() {
        qrService = new HmacFactoryQrPayloadService("usecase-detail-secret");
        LedInstanceMapper mapper = new LedInstanceMapper(qrService, new LedModelMapper());
        useCase = new GetLedInstanceDetailUseCase(
                ledInstanceRepository,
                ledModelRepository,
                customerLookupService,
                orderFulfillmentLookupService,
                mapper
        );
    }

    @Test
    @DisplayName("DEV-174 UC-01 [N]: Returns full unit details, model specs, NOT_ACTIVATED warranty, and signed QR payload for unbound unit")
    void execute_unboundInStockUnit_returnsModelSpecsAndQrPayload() {
        LedInstanceEntity entity = LedInstanceEntity.createInStock(
                101L,
                "LMT-64X64-0001",
                "AA:BB:CC:11:22:01",
                "REV_1_0",
                Instant.parse("2026-10-15T06:00:00Z")
        );
        ReflectionTestUtils.setField(entity, "instanceId", 1L);

        LedModelEntity modelEntity = LedModelEntity.createCatalogModel(
                101L,
                101L,
                "Lumintra Matrix 64x64 Pro",
                64,
                64,
                Map.of("Voltage", "5V USB-C"),
                20L,
                LedModelStatus.ACTIVE
        );

        when(ledInstanceRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(101L)).thenReturn(Optional.of(modelEntity));
        when(orderFulfillmentLookupService.findFulfillmentsByInstanceIds(List.of(1L))).thenReturn(Map.of());

        LedInstanceDetailResponse result = useCase.execute(1L);

        assertThat(result.instanceId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo(LedInstanceStatus.IN_STOCK);
        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.NOT_ACTIVATED);
        assertThat(result.model().modelId()).isEqualTo(101L);
        assertThat(result.model().modelName()).isEqualTo("Lumintra Matrix 64x64 Pro");
        assertThat(result.model().spec().widthPx()).isEqualTo(64);
        assertThat(result.model().spec().heightPx()).isEqualTo(64);
        assertThat(result.fulfillment()).isNull();
        assertThat(result.owner()).isNull();
        assertThat(result.qrPayload()).isEqualTo(qrService.generateQrPayload("LMT-64X64-0001", "AA:BB:CC:11:22:01"));

        verifyNoInteractions(customerLookupService);
        verify(orderFulfillmentLookupService).findFulfillmentsByInstanceIds(List.of(1L));
    }

    @Test
    @DisplayName("DEV-174 UC-02 [N]: Enriches owner, fulfillment, warranty status, and suppresses QR payload for BOUND unit")
    void execute_boundUnit_enrichesOwnerAndFulfillmentAndSuppressesQrPayload() {
        Instant boundAt = Instant.now().minus(5, ChronoUnit.DAYS);
        Instant warrantyExpiresAt = boundAt.plus(180, ChronoUnit.DAYS);

        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                101L,
                "LMT-64X64-0002",
                "AA:BB:CC:11:22:02",
                "REV_2_0",
                42L,
                "Front Grille",
                LedInstanceStatus.BOUND,
                Instant.parse("2026-10-01T06:00:00Z"),
                boundAt,
                warrantyExpiresAt
        );
        entity.setCurrentFirmwareId(9L);
        ReflectionTestUtils.setField(entity, "instanceId", 2L);

        LedModelEntity modelEntity = LedModelEntity.createCatalogModel(
                101L, 101L, "Lumintra Matrix 64x64 Pro", 64, 64, Map.of(), 10L, LedModelStatus.ACTIVE);

        when(ledInstanceRepository.findById(2L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(101L)).thenReturn(Optional.of(modelEntity));
        when(customerLookupService.findProfileByCustomerId(42L))
                .thenReturn(Optional.of(new CustomerProfileSnapshot(42L, "Nguyen Van A", "0901234567", "customer@example.com")));
        when(orderFulfillmentLookupService.findFulfillmentsByInstanceIds(List.of(2L)))
                .thenReturn(Map.of(2L, new InstanceFulfillmentSnapshot(2L, 55L, 102L)));

        LedInstanceDetailResponse result = useCase.execute(2L);

        assertThat(result.instanceId()).isEqualTo(2L);
        assertThat(result.currentFirmwareId()).isEqualTo(9L);
        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.ACTIVE);
        assertThat(result.qrPayload()).isNull();
        assertThat(result.fulfillment()).isEqualTo(new LedInstanceDetailResponse.FulfillmentInfo(55L, 102L));
        assertThat(result.owner()).isEqualTo(new LedInstanceDetailResponse.OwnerInfo(
                42L, "Nguyen Van A", "0901234567", "customer@example.com"));
    }

    @Test
    @DisplayName("DEV-174 UC-03 [B]: Handles missing model row and empty customer/fulfillment lookups gracefully")
    void execute_missingEnrichmentRows_returnsPartialSafely() {
        LedInstanceEntity entity = LedInstanceEntity.createWithState(
                999L,
                "LMT-64X64-0003",
                "AA:BB:CC:11:22:03",
                "REV_1_0",
                77L,
                null,
                LedInstanceStatus.RETURNED,
                null,
                Instant.now().minus(200, ChronoUnit.DAYS),
                Instant.now().minus(20, ChronoUnit.DAYS)
        );
        ReflectionTestUtils.setField(entity, "instanceId", 3L);

        when(ledInstanceRepository.findById(3L)).thenReturn(Optional.of(entity));
        when(ledModelRepository.findById(999L)).thenReturn(Optional.empty());
        when(customerLookupService.findProfileByCustomerId(77L)).thenReturn(Optional.empty());
        when(orderFulfillmentLookupService.findFulfillmentsByInstanceIds(List.of(3L))).thenReturn(Map.of());

        LedInstanceDetailResponse result = useCase.execute(3L);

        assertThat(result.warrantyStatus()).isEqualTo(WarrantyStatus.EXPIRED);
        assertThat(result.model()).isEqualTo(
                LedInstanceDetailResponse.ModelInfo.builder().modelId(999L).build());
        assertThat(result.owner()).isEqualTo(
                new LedInstanceDetailResponse.OwnerInfo(77L, null, null, null));
        assertThat(result.fulfillment()).isNull();
        assertThat(result.qrPayload()).isNotNull();
    }

    @Test
    @DisplayName("DEV-174 UC-04 [A]: Throws LedInstanceNotFoundException when instanceId does not exist")
    void execute_unknownInstanceId_throwsLedInstanceNotFoundException() {
        when(ledInstanceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(404L))
                .isInstanceOf(LedInstanceNotFoundException.class)
                .hasMessage("LED instance not found.");

        verifyNoInteractions(ledModelRepository, customerLookupService, orderFulfillmentLookupService);
    }
}
