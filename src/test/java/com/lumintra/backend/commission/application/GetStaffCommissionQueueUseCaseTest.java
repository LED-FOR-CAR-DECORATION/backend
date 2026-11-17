package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.StaffCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.StaffCommissionQueueResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetStaffCommissionQueueUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRepository;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private EmployeeLookupService employeeLookupService;

    private CommissionRequestMapper commissionMapper;
    private GetStaffCommissionQueueUseCase useCase;

    @BeforeEach
    void setUp() {
        commissionMapper = new CommissionRequestMapper();
        useCase = new GetStaffCommissionQueueUseCase(
                commissionRepository,
                commissionMapper,
                customerLookupService,
                employeeLookupService
        );
    }

    private CommissionRequestEntity createSampleEntity(Long id, Long customerId, CommissionStatus status, Long staffId, Long designerId) {
        CommissionRequestEntity entity = CommissionRequestEntity.createPendingReview(
                customerId,
                "Sample Commission " + id,
                "Description for " + id,
                "ADVANCED",
                "32x64",
                "{}",
                new BigDecimal("250000.00"),
                2,
                staffId
        );
        entity.setCommissionId(id);
        entity.setStatus(status);
        entity.setAssignedDesignerId(designerId);
        entity.setSlaDueAt(Instant.parse("2026-11-20T10:00:00Z"));
        return entity;
    }

    @Test
    @DisplayName("CMS-06 UseCase UTCID01 [N]: Without status filter, queries all commissions globally and enriches metadata")
    void execute_withoutStatusFilter_queriesAllGloballyAndEnrichesProfiles() {
        CommissionRequestEntity entity1 = createSampleEntity(101L, 10L, CommissionStatus.PENDING_REVIEW, 2L, null);
        CommissionRequestEntity entity2 = createSampleEntity(102L, 11L, CommissionStatus.PAID, 2L, 5L);

        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(entity1, entity2), PageRequest.of(0, 20), 2);
        when(commissionRepository.findAll(any(Pageable.class))).thenReturn(page);

        when(customerLookupService.findProfilesByCustomerIds(eq(Set.of(10L, 11L))))
                .thenReturn(Map.of(
                        10L, new CustomerProfileSnapshot(10L, "Nguyen Van A", "0901234567", "nguyenvana@gmail.com"),
                        11L, new CustomerProfileSnapshot(11L, "Tran Thi B", "0912345678", "tranthib@gmail.com")
                ));

        when(employeeLookupService.findProfilesByEmployeeIds(eq(Set.of(2L, 5L))))
                .thenReturn(Map.of(
                        2L, new EmployeeProfileSnapshot(2L, "Staff Hoang", "staff@lumintra.com"),
                        5L, new EmployeeProfileSnapshot(5L, "Designer Nam", "designer@lumintra.com")
                ));

        StaffCommissionQueueFilterRequest request = new StaffCommissionQueueFilterRequest(null, "DESC", 0, 20);

        StaffCommissionQueueResponse response = useCase.execute(request);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(2);
        assertThat(response.meta().getTotalElements()).isEqualTo(2);

        var item1 = response.items().get(0);
        assertThat(item1.commissionId()).isEqualTo(101L);
        assertThat(item1.customerId()).isEqualTo(10L);
        assertThat(item1.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item1.customerEmail()).isEqualTo("nguyenvana@gmail.com");
        assertThat(item1.status()).isEqualTo(CommissionStatus.PENDING_REVIEW);
        assertThat(item1.assignedStaffId()).isEqualTo(2L);
        assertThat(item1.assignedStaffName()).isEqualTo("Staff Hoang");
        assertThat(item1.assignedDesignerId()).isNull();
        assertThat(item1.assignedDesignerName()).isNull();

        var item2 = response.items().get(1);
        assertThat(item2.commissionId()).isEqualTo(102L);
        assertThat(item2.customerName()).isEqualTo("Tran Thi B");
        assertThat(item2.status()).isEqualTo(CommissionStatus.PAID);
        assertThat(item2.assignedStaffName()).isEqualTo("Staff Hoang");
        assertThat(item2.assignedDesignerId()).isEqualTo(5L);
        assertThat(item2.assignedDesignerName()).isEqualTo("Designer Nam");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commissionRepository).findAll(pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(0);
        assertThat(captured.getPageSize()).isEqualTo(20);
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @ParameterizedTest
    @EnumSource(CommissionStatus.class)
    @DisplayName("CMS-06 UseCase UTCID02 [N]: Supports filtering across all 13 commission lifecycle states")
    void execute_withStatusFilter_queriesOnlyFilteredStatus(CommissionStatus targetStatus) {
        CommissionRequestEntity entity = createSampleEntity(200L, 10L, targetStatus, 2L, null);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(entity));
        when(commissionRepository.findAllByStatus(eq(targetStatus), any(Pageable.class))).thenReturn(page);

        when(customerLookupService.findProfilesByCustomerIds(any())).thenReturn(Collections.emptyMap());
        when(employeeLookupService.findProfilesByEmployeeIds(any())).thenReturn(Collections.emptyMap());

        StaffCommissionQueueFilterRequest request = new StaffCommissionQueueFilterRequest(targetStatus, "DESC", 0, 10);
        StaffCommissionQueueResponse response = useCase.execute(request);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).status()).isEqualTo(targetStatus);
        verify(commissionRepository).findAllByStatus(eq(targetStatus), any(Pageable.class));
    }

    @Test
    @DisplayName("CMS-06 UseCase UTCID03 [B]: When queue is empty, returns empty response without calling lookup services")
    void execute_emptyPage_returnsEmptyResponseImmediately() {
        Page<CommissionRequestEntity> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(commissionRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        StaffCommissionQueueResponse response = useCase.execute(null);

        assertThat(response.items()).isEmpty();
        assertThat(response.meta().getTotalElements()).isEqualTo(0);
        verifyNoInteractions(customerLookupService);
        verifyNoInteractions(employeeLookupService);
    }

    @Test
    @DisplayName("CMS-06 UseCase UTCID04 [A]: Handles missing profile lookups gracefully with null names")
    void execute_profilesNotFound_returnsNullNamesGracefully() {
        CommissionRequestEntity entity = createSampleEntity(300L, 99L, CommissionStatus.IN_PROGRESS, 88L, 77L);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(entity));
        when(commissionRepository.findAllByStatus(eq(CommissionStatus.IN_PROGRESS), any(Pageable.class))).thenReturn(page);

        when(customerLookupService.findProfilesByCustomerIds(eq(Set.of(99L)))).thenReturn(Collections.emptyMap());
        when(employeeLookupService.findProfilesByEmployeeIds(eq(Set.of(88L, 77L)))).thenReturn(Collections.emptyMap());

        StaffCommissionQueueFilterRequest request = new StaffCommissionQueueFilterRequest(CommissionStatus.IN_PROGRESS, "ASC", 1, 15);
        StaffCommissionQueueResponse response = useCase.execute(request);

        assertThat(response.items()).hasSize(1);
        var item = response.items().get(0);
        assertThat(item.customerId()).isEqualTo(99L);
        assertThat(item.customerName()).isNull();
        assertThat(item.customerEmail()).isNull();
        assertThat(item.assignedStaffId()).isEqualTo(88L);
        assertThat(item.assignedStaffName()).isNull();
        assertThat(item.assignedDesignerId()).isEqualTo(77L);
        assertThat(item.assignedDesignerName()).isNull();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(commissionRepository).findAllByStatus(eq(CommissionStatus.IN_PROGRESS), pageableCaptor.capture());
        Pageable captured = pageableCaptor.getValue();
        assertThat(captured.getPageNumber()).isEqualTo(1);
        assertThat(captured.getPageSize()).isEqualTo(15);
        assertThat(captured.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
