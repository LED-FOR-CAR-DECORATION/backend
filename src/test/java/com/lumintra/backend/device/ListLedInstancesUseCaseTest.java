package com.lumintra.backend.device;

import com.lumintra.backend.device.api.request.LedInstanceFilterRequest;
import com.lumintra.backend.device.api.response.LedInstanceListResponse;
import com.lumintra.backend.device.application.ListLedInstancesUseCase;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.LedInstanceStatus;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.mapper.LedInstanceMapper;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.security.HmacFactoryQrPayloadService;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListLedInstancesUseCaseTest {

    @Mock
    private LedInstanceRepository ledInstanceRepository;

    @Mock
    private LedModelLookupService ledModelLookupService;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private OrderFulfillmentLookupService orderFulfillmentLookupService;

    private ListLedInstancesUseCase useCase;

    @BeforeEach
    void setUp() {
        LedInstanceMapper mapper = new LedInstanceMapper(new HmacFactoryQrPayloadService("usecase-secret"));
        useCase = new ListLedInstancesUseCase(
                ledInstanceRepository,
                ledModelLookupService,
                customerLookupService,
                orderFulfillmentLookupService,
                mapper
        );
    }

    @Test
    @DisplayName("DEV-173 UC-01 [N]: Queries repository sorted by instanceId DESC and batch-enriches models, owners, and fulfillments")
    void execute_populatedPage_enrichesInBatchAndMapsMeta() {
        LedInstanceEntity inStock = LedInstanceEntity.createInStock(
                101L, "LMT-001", "AA:BB:CC:00:00:01", "REV_1_0", Instant.now());
        ReflectionTestUtils.setField(inStock, "instanceId", 11L);

        LedInstanceEntity bound = LedInstanceEntity.createWithState(
                102L,
                "LMT-002",
                "AA:BB:CC:00:00:02",
                "REV_1_0",
                42L,
                "Car Display",
                LedInstanceStatus.BOUND,
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(86400)
        );
        ReflectionTestUtils.setField(bound, "instanceId", 10L);

        PageRequest expectedPage = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "instanceId"));
        Page<LedInstanceEntity> page = new PageImpl<>(List.of(bound, inStock), expectedPage, 4L);

        when(ledInstanceRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(ledModelLookupService.findByModelIds(Set.of(102L, 101L))).thenReturn(Map.of(
                101L, new LedModelSnapshot(101L, "Matrix Pro", Map.of(), 10L),
                102L, new LedModelSnapshot(102L, "Matrix Compact", Map.of(), 5L)
        ));
        when(customerLookupService.findProfilesByCustomerIds(Set.of(42L))).thenReturn(Map.of(
                42L, new CustomerProfileSnapshot(42L, "Customer One", "0909000111", "c1@example.com")
        ));
        when(orderFulfillmentLookupService.findFulfillmentsByInstanceIds(Set.of(10L, 11L))).thenReturn(Map.of(
                10L, new InstanceFulfillmentSnapshot(10L, 99L, 301L)
        ));

        LedInstanceFilterRequest filter = new LedInstanceFilterRequest(1, 2, null, null, null);
        LedInstanceListResponse response = useCase.execute(filter);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(ledInstanceRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("instanceId"))
                .isNotNull()
                .extracting(Sort.Order::getDirection)
                .isEqualTo(Sort.Direction.DESC);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).modelName()).isEqualTo("Matrix Compact");
        assertThat(response.items().get(0).fulfillment()).isEqualTo(new LedInstanceListResponse.FulfillmentInfo(99L, 301L));
        assertThat(response.items().get(0).owner().customerId()).isEqualTo(42L);
        assertThat(response.items().get(0).qrPayload()).isNull();

        assertThat(response.items().get(1).modelName()).isEqualTo("Matrix Pro");
        assertThat(response.items().get(1).fulfillment()).isNull();
        assertThat(response.items().get(1).owner()).isNull();
        assertThat(response.items().get(1).qrPayload()).isNotBlank();

        assertThat(response.meta().getTotalElements()).isEqualTo(4L);
    }

    @Test
    @DisplayName("DEV-173 UC-02 [B]: Skips customerLookupService when no instances have ownerCustomerId, and skips all batch calls when empty")
    void execute_emptyPageOrNoOwners_skipsUnusedLookups() {
        PageRequest defaultPageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "instanceId"));
        when(ledInstanceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), defaultPageable, 0L));

        LedInstanceListResponse emptyResponse = useCase.execute(null);

        assertThat(emptyResponse.items()).isEmpty();
        assertThat(emptyResponse.meta().getTotalElements()).isZero();
        verifyNoInteractions(ledModelLookupService, customerLookupService, orderFulfillmentLookupService);

        LedInstanceEntity unbound = LedInstanceEntity.createInStock(101L, "LMT-003", "AA:BB:CC:00:00:03", "REV_1_0", null);
        ReflectionTestUtils.setField(unbound, "instanceId", 3L);
        when(ledInstanceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(unbound), defaultPageable, 1L));
        when(ledModelLookupService.findByModelIds(Set.of(101L))).thenReturn(Map.of());
        when(orderFulfillmentLookupService.findFulfillmentsByInstanceIds(Set.of(3L))).thenReturn(Map.of());

        LedInstanceListResponse unboundOnlyResponse = useCase.execute(new LedInstanceFilterRequest(null, null, null, null, null));

        assertThat(unboundOnlyResponse.items()).hasSize(1);
        verifyNoInteractions(customerLookupService);
    }
}
