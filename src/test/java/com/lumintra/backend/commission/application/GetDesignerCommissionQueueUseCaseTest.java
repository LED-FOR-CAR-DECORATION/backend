package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.DesignerCommissionQueueFilterRequest;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse;
import com.lumintra.backend.commission.api.response.DesignerCommissionQueueResponse.DesignerCommissionQueueItem;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.response.PaginationMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-14: GetDesignerCommissionQueueUseCase Unit Tests")
class GetDesignerCommissionQueueUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRepository;

    @Mock
    private CommissionRequestMapper commissionMapper;

    @Mock
    private CustomerLookupService customerLookupService;

    @Mock
    private EmployeeLookupService employeeLookupService;

    @InjectMocks
    private GetDesignerCommissionQueueUseCase useCase;

    private CommissionRequestEntity sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(101L)
                .customerId(501L)
                .title("Neon Cyber Samurai")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .status(CommissionStatus.IN_PROGRESS)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .initialPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .revisionsUsed(0)
                .maxRevisions(2)
                .isSlaOverdue(false)
                .slaDueAt(Instant.parse("2026-11-20T12:00:00Z"))
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: Designer views assigned queue without status filter -> returns enriched paginated tickets")
    void execute_WhenDesignerHasAssignedCommissions_ReturnsEnrichedQueueResponse() {
        // Given
        Long designerId = 5L;
        DesignerCommissionQueueFilterRequest filter = new DesignerCommissionQueueFilterRequest(null, "DESC", 0, 20);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(sampleCommission));

        when(commissionRepository.findAllByAssignedDesignerId(eq(designerId), any(Pageable.class)))
                .thenReturn(page);

        CustomerProfileSnapshot customerProfile = new CustomerProfileSnapshot(501L, "Nguyen Van A", "0900000001", "a@test.com");
        EmployeeProfileSnapshot staffProfile = new EmployeeProfileSnapshot(3L, "Tran Thi Staff", "staff@test.com");
        EmployeeProfileSnapshot designerProfile = new EmployeeProfileSnapshot(5L, "Le Van Designer", "designer@test.com");

        when(customerLookupService.findProfilesByCustomerIds(Set.of(501L)))
                .thenReturn(Map.of(501L, customerProfile));
        when(employeeLookupService.findProfilesByEmployeeIds(Set.of(3L, 5L)))
                .thenReturn(Map.of(3L, staffProfile, 5L, designerProfile));

        DesignerCommissionQueueItem queueItem = DesignerCommissionQueueItem.builder()
                .commissionId(101L)
                .customerId(501L)
                .customerName("Nguyen Van A")
                .customerEmail("a@test.com")
                .title("Neon Cyber Samurai")
                .tierCode("ADVANCED")
                .targetMatrixDimensions("32x64")
                .status(CommissionStatus.IN_PROGRESS)
                .assignedStaffId(3L)
                .assignedStaffName("Tran Thi Staff")
                .assignedDesignerId(5L)
                .assignedDesignerName("Le Van Designer")
                .isSlaOverdue(false)
                .slaDueAt(Instant.parse("2026-11-20T12:00:00Z"))
                .build();

        DesignerCommissionQueueResponse expectedResponse = DesignerCommissionQueueResponse.builder()
                .items(List.of(queueItem))
                .meta(PaginationMeta.from(page))
                .build();

        when(commissionMapper.toDesignerQueueResponse(eq(page), any(), any()))
                .thenReturn(expectedResponse);

        // When
        DesignerCommissionQueueResponse response = useCase.execute(designerId, filter);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        DesignerCommissionQueueItem item = response.items().getFirst();
        assertThat(item.commissionId()).isEqualTo(101L);
        assertThat(item.customerName()).isEqualTo("Nguyen Van A");
        assertThat(item.assignedStaffName()).isEqualTo("Tran Thi Staff");
        assertThat(item.assignedDesignerName()).isEqualTo("Le Van Designer");
        assertThat(item.status()).isEqualTo(CommissionStatus.IN_PROGRESS);

        verify(commissionRepository).findAllByAssignedDesignerId(eq(designerId), any(Pageable.class));
    }

    @Test
    @DisplayName("UTCID02 [N]: Designer views queue with status filter (IN_PROGRESS) -> queries repository with status")
    void execute_WhenStatusFilterProvided_QueriesByStatusAndReturnsFilteredQueue() {
        // Given
        Long designerId = 5L;
        DesignerCommissionQueueFilterRequest filter = new DesignerCommissionQueueFilterRequest(CommissionStatus.IN_PROGRESS, "DESC", 0, 10);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(sampleCommission));

        when(commissionRepository.findAllByAssignedDesignerIdAndStatus(eq(designerId), eq(CommissionStatus.IN_PROGRESS), any(Pageable.class)))
                .thenReturn(page);

        DesignerCommissionQueueResponse expectedResponse = DesignerCommissionQueueResponse.builder()
                .items(List.of())
                .meta(PaginationMeta.from(page))
                .build();

        when(commissionMapper.toDesignerQueueResponse(eq(page), any(), any()))
                .thenReturn(expectedResponse);

        // When
        DesignerCommissionQueueResponse response = useCase.execute(designerId, filter);

        // Then
        assertThat(response).isNotNull();
        verify(commissionRepository).findAllByAssignedDesignerIdAndStatus(eq(designerId), eq(CommissionStatus.IN_PROGRESS), any(Pageable.class));
    }

    @Test
    @DisplayName("UTCID03 [N]: Designer has no assigned tickets -> returns empty queue cleanly without profile lookups")
    void execute_WhenDesignerHasNoAssignedCommissions_ReturnsEmptyQueueResponse() {
        // Given
        Long designerId = 99L;
        DesignerCommissionQueueFilterRequest filter = new DesignerCommissionQueueFilterRequest();
        Page<CommissionRequestEntity> emptyPage = Page.empty();

        when(commissionRepository.findAllByAssignedDesignerId(eq(designerId), any(Pageable.class)))
                .thenReturn(emptyPage);

        DesignerCommissionQueueResponse emptyResponse = DesignerCommissionQueueResponse.builder()
                .items(List.of())
                .meta(PaginationMeta.from(emptyPage))
                .build();

        when(commissionMapper.toDesignerQueueResponse(emptyPage, Map.of(), Map.of()))
                .thenReturn(emptyResponse);

        // When
        DesignerCommissionQueueResponse response = useCase.execute(designerId, filter);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
        verify(commissionRepository).findAllByAssignedDesignerId(eq(designerId), any(Pageable.class));
    }

    @Test
    @DisplayName("UTCID04 [B]: Designer views queue with custom pagination parameters (page=1, size=5, ASC)")
    void execute_WhenCustomPaginationProvided_UsesPageableProperly() {
        // Given
        Long designerId = 5L;
        DesignerCommissionQueueFilterRequest filter = new DesignerCommissionQueueFilterRequest(null, "ASC", 1, 5);
        Page<CommissionRequestEntity> page = new PageImpl<>(List.of(sampleCommission));

        when(commissionRepository.findAllByAssignedDesignerId(eq(designerId), any(Pageable.class)))
                .thenReturn(page);

        DesignerCommissionQueueResponse expectedResponse = DesignerCommissionQueueResponse.builder()
                .items(List.of())
                .meta(PaginationMeta.from(page))
                .build();

        when(commissionMapper.toDesignerQueueResponse(eq(page), any(), any()))
                .thenReturn(expectedResponse);

        // When
        DesignerCommissionQueueResponse response = useCase.execute(designerId, filter);

        // Then
        assertThat(response).isNotNull();
        verify(commissionRepository).findAllByAssignedDesignerId(eq(designerId), any(Pageable.class));
    }
}
