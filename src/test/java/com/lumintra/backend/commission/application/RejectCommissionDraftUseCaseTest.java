package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.RejectCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.RejectCommissionDraftResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.CommissionSubmissionNotFoundException;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-16: RejectCommissionDraftUseCase Unit Tests")
class RejectCommissionDraftUseCaseTest {

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionSubmissionRepository commissionSubmissionRepository;

    @Spy
    private CommissionRequestMapper commissionRequestMapper = new CommissionRequestMapper();

    @InjectMocks
    private RejectCommissionDraftUseCase useCase;

    private CommissionRequestEntity sampleCommission;
    private CommissionSubmissionEntity pendingSubmission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .title("Cyberpunk Neon Cat Animation")
                .status(CommissionStatus.INTERNAL_REVIEW)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .initialPrice(BigDecimal.valueOf(350000))
                .totalPrice(BigDecimal.valueOf(350000))
                .revisionsUsed(1)
                .maxRevisions(2)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        pendingSubmission = CommissionSubmissionEntity.builder()
                .submissionId(42L)
                .commissionId(105L)
                .designerId(5L)
                .versionNumber(2)
                .imageUrl("https://cdn.lumintra.shop/commissions/105/drafts/v2.gif")
                .status(CommissionSubmissionStatus.PENDING_STAFF_REVIEW)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: Assigned staff rejects latest pending draft (submissionId null) -> STAFF_REJECTED, 0 customer revision quota consumed, INTERNAL_REVISION_REQUIRED")
    void execute_WhenLatestPendingDraftAndAssignedStaff_RejectsInternallyWithoutConsumingQuota() {
        // Given
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "  Increase neon edge brightness  ");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                105L, CommissionSubmissionStatus.PENDING_STAFF_REVIEW))
                .thenReturn(Optional.of(pendingSubmission));
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RejectCommissionDraftResponse response = useCase.execute(105L, request, 3L, false);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.submissionId()).isEqualTo(42L);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_REJECTED);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVISION_REQUIRED);
        assertThat(response.reviewerStaffId()).isEqualTo(3L);
        assertThat(response.staffReviewNote()).isEqualTo("Increase neon edge brightness");
        assertThat(response.revisionsUsed()).isEqualTo(1); // AC02: 0 customer revision quota consumed
        assertThat(response.maxRevisions()).isEqualTo(2);
        assertThat(response.reviewedAt()).isNotNull();

        ArgumentCaptor<CommissionSubmissionEntity> subCaptor = ArgumentCaptor.forClass(CommissionSubmissionEntity.class);
        verify(commissionSubmissionRepository).save(subCaptor.capture());
        assertThat(subCaptor.getValue().getStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_REJECTED);
        assertThat(subCaptor.getValue().getReviewerStaffId()).isEqualTo(3L);

        ArgumentCaptor<CommissionRequestEntity> commCaptor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(commCaptor.capture());
        assertThat(commCaptor.getValue().getStatus()).isEqualTo(CommissionStatus.INTERNAL_REVISION_REQUIRED);
        assertThat(commCaptor.getValue().getRevisionsUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTCID02 [N]: Assigned staff rejects explicit submissionId -> STAFF_REJECTED, 0 quota consumed, INTERNAL_REVISION_REQUIRED")
    void execute_WhenExplicitSubmissionIdProvided_RejectsSpecificDraft() {
        // Given
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(42L, "Smooth animation loop");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(42L, 105L))
                .thenReturn(Optional.of(pendingSubmission));
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RejectCommissionDraftResponse response = useCase.execute(105L, request, 3L, false);

        // Then
        assertThat(response.submissionId()).isEqualTo(42L);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_REJECTED);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVISION_REQUIRED);
        assertThat(response.revisionsUsed()).isEqualTo(1);
    }

    @Test
    @DisplayName("UTCID03 [N]: Manager/Admin bypasses staff lock and rejects draft internally")
    void execute_WhenPrivilegedManagerOverridesLock_RejectsDraftSuccessfully() {
        // Given - locked by staff 3, caller is manager 99
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "QA override: fix alignment");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                105L, CommissionSubmissionStatus.PENDING_STAFF_REVIEW))
                .thenReturn(Optional.of(pendingSubmission));
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RejectCommissionDraftResponse response = useCase.execute(105L, request, 99L, true);

        // Then
        assertThat(response.reviewerStaffId()).isEqualTo(99L);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_REJECTED);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVISION_REQUIRED);
    }

    @Test
    @DisplayName("UTCID04 [N]: Unassigned commission auto-assigns callerStaffId and rejects draft")
    void execute_WhenAssignedStaffIsNull_AutoAssignsStaffAndRejectsDraft() {
        // Given
        sampleCommission.setAssignedStaffId(null);
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "Fix color contrast");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                105L, CommissionSubmissionStatus.PENDING_STAFF_REVIEW))
                .thenReturn(Optional.of(pendingSubmission));
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        RejectCommissionDraftResponse response = useCase.execute(105L, request, 7L, false);

        // Then
        assertThat(response.reviewerStaffId()).isEqualTo(7L);
        assertThat(sampleCommission.getAssignedStaffId()).isEqualTo(7L);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVISION_REQUIRED);
    }

    @Test
    @DisplayName("UTCID05 [A]: Commission not found -> throws CommissionNotFoundException (404)")
    void execute_WhenCommissionNotFound_ThrowsCommissionNotFoundException() {
        // Given
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "Needs revision");
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(999L, request, 3L, false))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("not found with id: 999");

        verifyNoInteractions(commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Regular staff attempts to review commission locked by another staff -> throws CommissionStaffLockException (403)")
    void execute_WhenLockedByAnotherStaff_ThrowsCommissionStaffLockException() {
        // Given - locked by staff 3, caller is staff 8
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "Needs revision");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 8L, false))
                .isInstanceOf(CommissionStaffLockException.class)
                .hasMessageContaining("locked by assigned staff 3");

        verifyNoInteractions(commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID07 [A]: Commission status is not INTERNAL_REVIEW -> throws InvalidCommissionStatusTransitionException (409)")
    void execute_WhenCommissionStatusNotInternalReview_ThrowsInvalidCommissionStatusTransitionException() {
        // Given
        sampleCommission.setStatus(CommissionStatus.IN_PROGRESS);
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "Needs revision");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("must be INTERNAL_REVIEW");

        verifyNoInteractions(commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID08 [A]: No pending draft submission found -> throws CommissionSubmissionNotFoundException (404)")
    void execute_WhenNoPendingSubmissionExists_ThrowsCommissionSubmissionNotFoundException() {
        // Given
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(null, "Needs revision");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(
                105L, CommissionSubmissionStatus.PENDING_STAFF_REVIEW))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 3L, false))
                .isInstanceOf(CommissionSubmissionNotFoundException.class)
                .hasMessageContaining("No pending draft submission found");

        verify(commissionSubmissionRepository, never()).save(any());
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID09 [A]: Explicit submissionId is not in PENDING_STAFF_REVIEW status -> throws InvalidCommissionStatusTransitionException (409)")
    void execute_WhenExplicitSubmissionNotPendingStaffReview_ThrowsInvalidCommissionStatusTransitionException() {
        // Given
        pendingSubmission.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);
        RejectCommissionDraftRequest request = new RejectCommissionDraftRequest(42L, "Already rejected");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(42L, 105L))
                .thenReturn(Optional.of(pendingSubmission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("must be PENDING_STAFF_REVIEW");

        verify(commissionSubmissionRepository, never()).save(any());
        verify(commissionRequestRepository, never()).save(any());
    }
}
