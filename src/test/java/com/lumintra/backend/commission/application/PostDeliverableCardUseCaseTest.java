package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.api.response.PostDeliverableCardResponse;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CMS-17: PostDeliverableCardUseCase Unit Tests")
class PostDeliverableCardUseCaseTest {

    private static final String DRAFT_URL = "https://cdn.lumintra.shop/commissions/105/drafts/v1-neon-cat.gif";

    @Mock
    private CommissionRequestRepository commissionRequestRepository;

    @Mock
    private CommissionSubmissionRepository commissionSubmissionRepository;

    @Mock
    private CommissionMessageRepository commissionMessageRepository;

    @Spy
    private CommissionMessageMapper commissionMessageMapper = new CommissionMessageMapper();

    @Mock
    private CommissionChatBroadcastPort chatBroadcastPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock(strictness = Mock.Strictness.LENIENT)
    private CommissionRevisionRequestRepository commissionRevisionRequestRepository;

    private com.lumintra.backend.commission.RevisionRequestFakes revisions;

    @InjectMocks
    private PostDeliverableCardUseCase useCase;

    private CommissionRequestEntity commission;
    private CommissionSubmissionEntity pendingDraft;

    @BeforeEach
    void setUp() {
        revisions = new com.lumintra.backend.commission.RevisionRequestFakes(commissionRevisionRequestRepository);
        commission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .title("Cyberpunk Neon Cat Animation")
                .status(CommissionStatus.INTERNAL_REVIEW)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .revisionsUsed(0)
                .maxRevisions(2)
                .initialPrice(BigDecimal.valueOf(350000))
                .totalPrice(BigDecimal.valueOf(350000))
                .build();

        pendingDraft = CommissionSubmissionEntity.builder()
                .submissionId(42L)
                .commissionId(105L)
                .designerId(5L)
                .versionNumber(1)
                .imageUrl(DRAFT_URL)
                .designerNote("Internal designer note")
                .status(CommissionSubmissionStatus.PENDING_STAFF_REVIEW)
                .build();
    }

    private void stubHappyPathPersistence() {
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionMessageRepository.save(any(CommissionMessageEntity.class))).thenAnswer(inv -> {
            CommissionMessageEntity entity = inv.getArgument(0);
            entity.setMessageId(1201L);
            entity.setCreatedAt(Instant.parse("2026-11-17T10:00:00Z"));
            return entity;
        });
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("UTCID01 [N]: Assigned staff approves pending draft -> STAFF_APPROVED, DELIVERABLE_CARD inserted, auto_complete_at +7d, PREVIEW_DELIVERED")
    void execute_WhenAssignedStaffAndPendingDraft_ApprovesDraftPostsCardAndDeliversPreview() throws Exception {
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.of(pendingDraft));
        stubHappyPathPersistence();
        Instant before = Instant.now();

        PostDeliverableCardResponse response = useCase.execute(105L, 3L, false);

        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(response.approvedSubmissionId()).isEqualTo(42L);
        assertThat(response.versionNumber()).isEqualTo(1);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_APPROVED);
        assertThat(response.autoCompleteAt()).isCloseTo(before.plus(Duration.ofDays(7)), within(Duration.ofSeconds(5)));

        ArgumentCaptor<CommissionSubmissionEntity> draftCaptor = ArgumentCaptor.forClass(CommissionSubmissionEntity.class);
        verify(commissionSubmissionRepository).save(draftCaptor.capture());
        CommissionSubmissionEntity savedDraft = draftCaptor.getValue();
        assertThat(savedDraft.getStatus()).isEqualTo(CommissionSubmissionStatus.STAFF_APPROVED);
        assertThat(savedDraft.getReviewerStaffId()).isEqualTo(3L);
        assertThat(savedDraft.getReviewedAt()).isNotNull();
        assertThat(savedDraft.getDeliveredAt()).isEqualTo(savedDraft.getReviewedAt());

        ArgumentCaptor<CommissionRequestEntity> commissionCaptor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(commissionCaptor.capture());
        assertThat(commissionCaptor.getValue().getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(commissionCaptor.getValue().getApprovedSubmissionId()).isEqualTo(42L);
        assertThat(commissionCaptor.getValue().getAutoCompleteAt()).isEqualTo(response.autoCompleteAt());
    }

    @Test
    @DisplayName("UTCID02 [N]: DELIVERABLE_CARD message carries STAFF sender, draft image and card metadata, and is broadcast to chat")
    void execute_WhenSuccessful_PersistsAndBroadcastsDeliverableCardMessage() throws Exception {
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.of(pendingDraft));
        stubHappyPathPersistence();

        PostDeliverableCardResponse response = useCase.execute(105L, 3L, false);

        ArgumentCaptor<CommissionMessageEntity> messageCaptor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(commissionMessageRepository).save(messageCaptor.capture());
        CommissionMessageEntity card = messageCaptor.getValue();
        assertThat(card.getCommissionId()).isEqualTo(105L);
        assertThat(card.getSenderId()).isEqualTo(3L);
        assertThat(card.getSenderRole()).isEqualTo(CommissionMessageSenderRole.STAFF);
        assertThat(card.getMessageType()).isEqualTo(CommissionMessageType.DELIVERABLE_CARD);
        assertThat(card.getMediaUrl()).isEqualTo(DRAFT_URL);
        assertThat(card.getContent()).isNotBlank();

        JsonNode metadata = new ObjectMapper().readTree(card.getMetadataJson());
        assertThat(metadata.get("submissionId").asLong()).isEqualTo(42L);
        assertThat(metadata.get("versionNumber").asInt()).isEqualTo(1);
        assertThat(metadata.get("imageUrl").asText()).isEqualTo(DRAFT_URL);
        assertThat(metadata.get("revisionsUsed").asInt()).isZero();
        assertThat(metadata.get("maxRevisions").asInt()).isEqualTo(2);
        assertThat(metadata.get("autoCompleteAt").asText()).isEqualTo(response.autoCompleteAt().toString());
        assertThat(card.getMetadataJson()).doesNotContain("Internal designer note");

        assertThat(response.deliverableCard().messageId()).isEqualTo(1201L);
        assertThat(response.deliverableCard().messageType()).isEqualTo(CommissionMessageType.DELIVERABLE_CARD);
        ArgumentCaptor<CommissionMessageResponse> broadcastCaptor = ArgumentCaptor.forClass(CommissionMessageResponse.class);
        verify(chatBroadcastPort).broadcastMessage(eq(105L), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue()).isEqualTo(response.deliverableCard());
    }

    @Test
    @DisplayName("UTCID03 [N]: MANAGER/ADMIN override can post the card on a commission locked by another staff")
    void execute_WhenPrivilegedManagementOverride_PostsCard() {
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.of(pendingDraft));
        stubHappyPathPersistence();

        PostDeliverableCardResponse response = useCase.execute(105L, 9L, true);

        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(commission.getAssignedStaffId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("UTCID04 [A]: Commission not found -> CommissionNotFoundException")
    void execute_WhenCommissionNotFound_ThrowsCommissionNotFoundException() {
        when(commissionRequestRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, 3L, false))
                .isInstanceOf(CommissionNotFoundException.class)
                .hasMessageContaining("999");

        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort);
    }

    @Test
    @DisplayName("UTCID05 [A]: Non-assigned staff without override -> CommissionStaffLockException, nothing persisted")
    void execute_WhenCallerIsNotAssignedStaff_ThrowsCommissionStaffLockException() {
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> useCase.execute(105L, 4L, false))
                .isInstanceOf(CommissionStaffLockException.class)
                .hasMessageContaining("locked by assigned staff 3");

        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort);
    }

    @Test
    @DisplayName("UTCID06 [A]: Commission not in INTERNAL_REVIEW -> InvalidCommissionStatusTransitionException")
    void execute_WhenStatusIsNotInternalReview_ThrowsInvalidStatusTransition() {
        commission.setStatus(CommissionStatus.IN_PROGRESS);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("must be INTERNAL_REVIEW");

        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort);
    }

    @Test
    @DisplayName("UTCID07 [B]: Latest draft is not PENDING_STAFF_REVIEW -> InvalidCommissionStatusTransitionException")
    void execute_WhenLatestDraftNotPendingReview_ThrowsInvalidStatusTransition() {
        pendingDraft.setStatus(CommissionSubmissionStatus.STAFF_REJECTED);
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.of(pendingDraft));

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("no draft pending staff review");

        verify(commissionSubmissionRepository, never()).save(any());
        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionMessageRepository, chatBroadcastPort);
    }

    @Test
    @DisplayName("UTCID08 [B]: No draft submitted at all -> InvalidCommissionStatusTransitionException")
    void execute_WhenNoDraftExists_ThrowsInvalidStatusTransition() {
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(105L, 3L, false))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);

        verifyNoInteractions(commissionMessageRepository, chatBroadcastPort);
    }

    @Test
    @DisplayName("Delivering a draft made for a revision completes that revision brief")
    void execute_WhenDraftAnswersRevision_CompletesRevisionRequest() {
        var revision = revisions.repository.save(
                com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity
                        .open(105L, 402L, 1, "Brighter outline", true, java.math.BigDecimal.ZERO));
        revision.confirmFree(1, Instant.now());
        pendingDraft.setRevisionRequestId(revision.getRevisionRequestId());
        when(commissionRequestRepository.findById(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdOrderByVersionNumberDesc(105L)).thenReturn(Optional.of(pendingDraft));
        stubHappyPathPersistence();

        useCase.execute(105L, 3L, false);

        assertThat(revision.getStatus())
                .isEqualTo(com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus.COMPLETED);
        assertThat(revision.getCompletedAt()).isNotNull();
    }
}
