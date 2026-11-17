package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.AssignCommissionRequest;
import com.lumintra.backend.commission.api.response.AssignCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.EmployeeDirectoryService;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidDesignerAssignmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-13: AssignCommissionToDesignerUseCase Unit Tests")
class AssignCommissionToDesignerUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private EmployeeDirectoryService employeeDirectoryService;

    @InjectMocks
    private AssignCommissionToDesignerUseCase useCase;

    private CommissionRequestEntity sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(101L)
                .customerId(501L)
                .title("Cyberpunk Neon Dragon")
                .status(CommissionStatus.PAID)
                .assignedStaffId(3L)
                .initialPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: Staff assigns PAID commission to active DESIGNER -> transitions to IN_PROGRESS and sets sla_due_at +72h")
    void execute_WhenValidStaffAndPaidCommissionAndActiveDesigner_AssignsSuccessfully() {
        // Given
        AssignCommissionRequest request = new AssignCommissionRequest(5L);
        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));
        when(employeeDirectoryService.isDesigner(5L)).thenReturn(true);
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Instant before = Instant.now();

        // When
        AssignCommissionResponse response = useCase.execute(101L, request, 3L, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.assignedDesignerId()).isEqualTo(5L);

        // Verify SLA is set to approximately +72h
        assertThat(response.slaDueAt()).isAfterOrEqualTo(before.plus(71, ChronoUnit.HOURS));
        assertThat(response.slaDueAt()).isBeforeOrEqualTo(Instant.now().plus(73, ChronoUnit.HOURS));

        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        CommissionRequestEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(saved.getAssignedDesignerId()).isEqualTo(5L);
        assertThat(saved.getSlaDueAt()).isNotNull();
    }

    @Test
    @DisplayName("UTCID02 [N]: Manager/Admin bypasses staff lock and successfully assigns PAID commission to active DESIGNER")
    void execute_WhenManagerOrAdminAndCommissionLockedByAnotherStaff_BypassesLockAndAssignsSuccessfully() {
        // Given - commission locked by staff 3, caller is manager 99
        AssignCommissionRequest request = new AssignCommissionRequest(7L);
        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));
        when(employeeDirectoryService.isDesigner(7L)).thenReturn(true);
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AssignCommissionResponse response = useCase.execute(101L, request, 99L, true);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(response.assignedDesignerId()).isEqualTo(7L);
        verify(commissionRequestRepository).save(sampleCommission);
    }

    @Test
    @DisplayName("UTCID03 [A]: Non-privileged staff attempts to assign ticket locked by another staff -> throws CommissionStaffLockException (403)")
    void execute_WhenStaffLockedByAnotherStaff_ThrowsCommissionStaffLockException() {
        // Given - commission locked by staff 3, caller is staff 4 (not privileged)
        AssignCommissionRequest request = new AssignCommissionRequest(5L);
        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(101L, request, 4L, false))
                .isInstanceOf(CommissionStaffLockException.class)
                .hasMessageContaining("locked by assigned staff 3");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID04 [A]: Commission request not found by ID -> throws CommissionNotFoundException (404)")
    void execute_WhenCommissionNotFound_ThrowsCommissionNotFoundException() {
        // Given
        AssignCommissionRequest request = new AssignCommissionRequest(5L);
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(999L, request, 3L, false))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("not found with id: 999");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: Commission status is not PAID (e.g. PENDING_REVIEW or PENDING_PAYMENT) -> throws InvalidCommissionStatusTransitionException (409)")
    void execute_WhenCommissionStatusNotPaid_ThrowsInvalidCommissionStatusTransitionException() {
        // Given
        sampleCommission.setStatus(CommissionStatus.PENDING_PAYMENT);
        AssignCommissionRequest request = new AssignCommissionRequest(5L);
        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(101L, request, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("must be PAID");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Target employee does not possess DESIGNER role or is inactive -> throws InvalidDesignerAssignmentException (400)")
    void execute_WhenTargetEmployeeNotActiveDesigner_ThrowsInvalidDesignerAssignmentException() {
        // Given
        AssignCommissionRequest request = new AssignCommissionRequest(88L);
        when(commissionRequestRepository.findById(101L)).thenReturn(Optional.of(sampleCommission));
        when(employeeDirectoryService.isDesigner(88L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> useCase.execute(101L, request, 3L, false))
                .isInstanceOf(InvalidDesignerAssignmentException.class)
                .hasMessageContaining("does not have the DESIGNER role");

        verify(commissionRequestRepository, never()).save(any());
    }
}
