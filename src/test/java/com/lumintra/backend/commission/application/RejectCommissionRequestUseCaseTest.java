package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.RejectCommissionRequest;
import com.lumintra.backend.commission.api.response.RejectCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-08: RejectCommissionRequestUseCase Unit Tests")
class RejectCommissionRequestUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Spy
    private CommissionRequestMapper commissionRequestMapper = new CommissionRequestMapper();

    @InjectMocks
    private RejectCommissionRequestUseCase useCase;

    private CommissionRequestEntity sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .title("Cyberpunk Neon Dragon")
                .status(CommissionStatus.PENDING_REVIEW)
                .assignedStaffId(3L)
                .initialPrice(BigDecimal.valueOf(500000))
                .totalPrice(BigDecimal.valueOf(500000))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: Staff rejects commission in PENDING_REVIEW status -> transitions to CANCELLED and saves rejectionReason")
    void execute_WhenAssignedStaffAndPendingReview_RejectsSuccessfully() {
        // Given
        RejectCommissionRequest request = new RejectCommissionRequest("Artwork request exceeds hardware matrix limits.");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RejectCommissionResponse response = useCase.execute(105L, request, 3L, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.status()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.rejectionReason()).isEqualTo("Artwork request exceeds hardware matrix limits.");

        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        CommissionRequestEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(saved.getRejectionReason()).isEqualTo("Artwork request exceeds hardware matrix limits.");
        assertThat(saved.getAssignedStaffId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("UTCID02 [N]: Commission has no assigned staff -> auto-assigns caller staff and transitions to CANCELLED")
    void execute_WhenUnassignedCommission_AutoAssignsStaffAndRejectsSuccessfully() {
        // Given
        sampleCommission.setAssignedStaffId(null);
        RejectCommissionRequest request = new RejectCommissionRequest("Inappropriate content submitted.");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RejectCommissionResponse response = useCase.execute(105L, request, 9L, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.status()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(response.assignedStaffId()).isEqualTo(9L);
        assertThat(response.rejectionReason()).isEqualTo("Inappropriate content submitted.");

        ArgumentCaptor<CommissionRequestEntity> captor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getAssignedStaffId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("UTCID03 [N]: Manager/Admin bypasses staff lock and successfully rejects commission")
    void execute_WhenManagerOrAdminAndCommissionLockedByAnotherStaff_BypassesLockAndRejects() {
        // Given - locked by staff 3, caller is manager 99
        RejectCommissionRequest request = new RejectCommissionRequest("Cancelled upon customer verbal request.");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RejectCommissionResponse response = useCase.execute(105L, request, 99L, true);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(CommissionStatus.CANCELLED);
        assertThat(response.assignedStaffId()).isEqualTo(3L);
        assertThat(response.rejectionReason()).isEqualTo("Cancelled upon customer verbal request.");
        verify(commissionRequestRepository).save(sampleCommission);
    }

    @Test
    @DisplayName("UTCID04 [A]: Commission request not found by ID -> throws CommissionNotFoundException (404)")
    void execute_WhenCommissionNotFound_ThrowsCommissionNotFoundException() {
        // Given
        RejectCommissionRequest request = new RejectCommissionRequest("Request not feasible.");
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(999L, request, 3L, false))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("not found with id: 999");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: Non-privileged staff attempts to reject ticket locked by another staff -> throws CommissionStaffLockException (403)")
    void execute_WhenStaffLockedByAnotherStaff_ThrowsCommissionStaffLockException() {
        // Given - commission locked by staff 3, caller is staff 4 (not privileged)
        RejectCommissionRequest request = new RejectCommissionRequest("Reject attempt by wrong staff.");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 4L, false))
                .isInstanceOf(CommissionStaffLockException.class)
                .hasMessageContaining("locked by assigned staff 3");

        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Commission status is not PENDING_REVIEW (e.g. PAID) -> throws InvalidCommissionStatusTransitionException (409)")
    void execute_WhenCommissionStatusNotPendingReview_ThrowsInvalidCommissionStatusTransitionException() {
        // Given
        sampleCommission.setStatus(CommissionStatus.PAID);
        RejectCommissionRequest request = new RejectCommissionRequest("Cannot reject after paid.");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("must be PENDING_REVIEW");

        verify(commissionRequestRepository, never()).save(any());
    }
}
