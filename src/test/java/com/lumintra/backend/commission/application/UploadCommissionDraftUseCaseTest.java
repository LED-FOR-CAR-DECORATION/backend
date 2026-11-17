package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.UploadCommissionDraftRequest;
import com.lumintra.backend.commission.api.response.UploadCommissionDraftResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.media.domain.service.MediaUrlValidator;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.media.InvalidMediaUrlException;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-15: UploadCommissionDraftUseCase Unit Tests")
class UploadCommissionDraftUseCaseTest {

    private static final String VALID_DRAFT_URL = "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif";

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionSubmissionRepository commissionSubmissionRepository;

    @Mock
    private MediaUrlValidator mediaUrlValidator;

    @Spy
    private CommissionRequestMapper commissionRequestMapper = new CommissionRequestMapper();

    @InjectMocks
    private UploadCommissionDraftUseCase useCase;

    private CommissionRequestEntity sampleCommission;

    @BeforeEach
    void setUp() {
        sampleCommission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .title("Cyberpunk Neon Cat Animation")
                .status(CommissionStatus.IN_PROGRESS)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .initialPrice(BigDecimal.valueOf(350000))
                .totalPrice(BigDecimal.valueOf(350000))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("UTCID01 [N]: Assigned designer uploads first draft for IN_PROGRESS commission -> version 1, PENDING_STAFF_REVIEW, status INTERNAL_REVIEW")
    void execute_WhenInProgressAndFirstDraft_CreatesVersion1AndTransitionsToInternalReview() {
        // Given
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(
                VALID_DRAFT_URL,
                "Initial animated neon cat draft"
        );
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        doNothing().when(mediaUrlValidator).validateMediaUrl(VALID_DRAFT_URL);
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L))
                .thenReturn(Optional.empty());
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> {
            CommissionSubmissionEntity entity = inv.getArgument(0);
            entity.setSubmissionId(42L);
            entity.setCreatedAt(Instant.parse("2026-11-17T10:50:00Z"));
            return entity;
        });
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        UploadCommissionDraftResponse response = useCase.execute(105L, request, 5L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.submissionId()).isEqualTo(42L);
        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.designerId()).isEqualTo(5L);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.imageUrl()).isEqualTo(VALID_DRAFT_URL);
        assertThat(response.designerNote()).isEqualTo("Initial animated neon cat draft");
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVIEW);

        ArgumentCaptor<CommissionSubmissionEntity> draftCaptor = ArgumentCaptor.forClass(CommissionSubmissionEntity.class);
        verify(commissionSubmissionRepository).save(draftCaptor.capture());
        assertThat(draftCaptor.getValue().getVersionNumber()).isEqualTo(1);
        assertThat(draftCaptor.getValue().getStatus()).isEqualTo(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);

        ArgumentCaptor<CommissionRequestEntity> commissionCaptor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(commissionCaptor.capture());
        assertThat(commissionCaptor.getValue().getStatus()).isEqualTo(CommissionStatus.INTERNAL_REVIEW);
    }

    @Test
    @DisplayName("UTCID02 [N]: Assigned designer uploads revised draft for INTERNAL_REVISION_REQUIRED commission -> increments to version 2 and transitions to INTERNAL_REVIEW")
    void execute_WhenInternalRevisionRequiredAndHasPreviousVersion_IncrementsVersionNumber() {
        // Given
        sampleCommission.setStatus(CommissionStatus.INTERNAL_REVISION_REQUIRED);
        CommissionSubmissionEntity latestDraft = CommissionSubmissionEntity.builder()
                .submissionId(42L)
                .commissionId(105L)
                .designerId(5L)
                .versionNumber(1)
                .imageUrl(VALID_DRAFT_URL)
                .status(CommissionSubmissionStatus.STAFF_REJECTED)
                .build();

        String v2Url = "https://cdn.lumintra.shop/commissions/105/drafts/v2-neon-cat.gif";
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(v2Url, "Adjusted contrast per QA feedback");

        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        doNothing().when(mediaUrlValidator).validateMediaUrl(v2Url);
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L))
                .thenReturn(Optional.of(latestDraft));
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> {
            CommissionSubmissionEntity entity = inv.getArgument(0);
            entity.setSubmissionId(43L);
            entity.setCreatedAt(Instant.parse("2026-11-17T11:30:00Z"));
            return entity;
        });
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        UploadCommissionDraftResponse response = useCase.execute(105L, request, 5L);

        // Then
        assertThat(response.submissionId()).isEqualTo(43L);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVIEW);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);
    }

    @Test
    @DisplayName("UTCID03 [N]: Assigned designer submits draft with blank/null designerNote -> succeeds with null note")
    void execute_WhenDesignerNoteIsBlank_PersistsNullNoteSuccessfully() {
        // Given
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(VALID_DRAFT_URL, "   ");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        doNothing().when(mediaUrlValidator).validateMediaUrl(VALID_DRAFT_URL);
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L))
                .thenReturn(Optional.empty());
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> {
            CommissionSubmissionEntity entity = inv.getArgument(0);
            entity.setSubmissionId(44L);
            return entity;
        });
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        UploadCommissionDraftResponse response = useCase.execute(105L, request, 5L);

        // Then
        assertThat(response.designerNote()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.INTERNAL_REVIEW);
    }

    @Test
    @DisplayName("UTCID04 [A]: Commission request not found -> throws CommissionNotFoundException (404)")
    void execute_WhenCommissionNotFound_ThrowsCommissionNotFoundException() {
        // Given
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(VALID_DRAFT_URL, "Note");
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> useCase.execute(999L, request, 5L))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("not found with id: 999");

        verifyNoInteractions(mediaUrlValidator, commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID05 [A]: Caller designer does not own assigned_designer_id -> throws CommissionOwnershipMismatchException (403)")
    void execute_WhenCallerIsNotAssignedDesigner_ThrowsCommissionOwnershipMismatchException() {
        // Given - assigned to designer 5, caller is designer 8
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(VALID_DRAFT_URL, "Note");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 8L))
                .isInstanceOf(CommissionOwnershipMismatchException.class)
                .hasMessageContaining("not assigned to designer 8");

        verifyNoInteractions(mediaUrlValidator, commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID06 [A]: Commission status is neither IN_PROGRESS nor INTERNAL_REVISION_REQUIRED -> throws InvalidCommissionStatusTransitionException (409)")
    void execute_WhenCommissionStatusInvalid_ThrowsInvalidCommissionStatusTransitionException() {
        // Given
        sampleCommission.setStatus(CommissionStatus.INTERNAL_REVIEW);
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(VALID_DRAFT_URL, "Note");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 5L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("IN_PROGRESS or INTERNAL_REVISION_REQUIRED");

        verifyNoInteractions(mediaUrlValidator, commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("UTCID07 [A]: imageUrl violates MED-01 URL domain rules -> throws InvalidMediaUrlException (400) without saving")
    void execute_WhenImageUrlInvalidDomain_ThrowsInvalidMediaUrlException() {
        // Given
        String untrustedUrl = "https://evil.example.com/malicious.gif";
        UploadCommissionDraftRequest request = new UploadCommissionDraftRequest(untrustedUrl, "Note");
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(sampleCommission));
        doThrow(new InvalidMediaUrlException("Submitted media URL is invalid or does not belong to authorized CDN / storage domains."))
                .when(mediaUrlValidator).validateMediaUrl(untrustedUrl);

        // When & Then
        assertThatThrownBy(() -> useCase.execute(105L, request, 5L))
                .isInstanceOf(InvalidMediaUrlException.class)
                .hasMessageContaining("authorized CDN / storage domains");

        verifyNoInteractions(commissionSubmissionRepository);
        verify(commissionRequestRepository, never()).save(any());
    }
}
