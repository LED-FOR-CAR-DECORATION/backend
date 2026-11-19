package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.ApproveDeliverableCardResponse;
import com.lumintra.backend.commission.application.service.CommissionCompletionService;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.event.CommissionCompletedEvent;
import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;
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
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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
@DisplayName("CMS-18: ApproveDeliverableCardUseCase Unit Tests")
class ApproveDeliverableCardUseCaseTest {

    private static final String PREVIEW_URL = "https://cdn.lumintra.shop/commissions/105/drafts/v2-neon-cat.gif";

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

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private CommissionRevisionRequestRepository revisionRequestRepository;

    private ApproveDeliverableCardUseCase useCase;

    private CommissionRequestEntity commission;
    private CommissionSubmissionEntity deliveredPreview;

    @BeforeEach
    void setUp() {
        useCase = new ApproveDeliverableCardUseCase(commissionRequestRepository, new CommissionCompletionService(
                commissionRequestRepository, commissionSubmissionRepository, commissionMessageRepository,
                revisionRequestRepository, commissionMessageMapper, chatBroadcastPort, eventPublisher, new ObjectMapper()));

        commission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .title("Cyberpunk Neon Cat Animation")
                .status(CommissionStatus.PREVIEW_DELIVERED)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .approvedSubmissionId(43L)
                .autoCompleteAt(Instant.now().plus(Duration.ofDays(7)))
                .revisionsUsed(1)
                .maxRevisions(2)
                .initialPrice(BigDecimal.valueOf(350000))
                .totalPrice(BigDecimal.valueOf(350000))
                .build();

        deliveredPreview = CommissionSubmissionEntity.builder()
                .submissionId(43L)
                .commissionId(105L)
                .designerId(5L)
                .versionNumber(2)
                .imageUrl(PREVIEW_URL)
                .status(CommissionSubmissionStatus.STAFF_APPROVED)
                .reviewerStaffId(3L)
                .build();
    }

    private void stubHappyPathPersistence() {
        when(commissionSubmissionRepository.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionRequestRepository.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissionMessageRepository.save(any(CommissionMessageEntity.class))).thenAnswer(inv -> {
            CommissionMessageEntity entity = inv.getArgument(0);
            entity.setMessageId(1301L);
            entity.setCreatedAt(Instant.parse("2026-11-18T10:00:00Z"));
            return entity;
        });
    }

    @Test
    @DisplayName("UTCID01 [N]: Owning customer approves card -> CUSTOMER_ACCEPTED, COMPLETED, approved_submission_id set, event published")
    void execute_WhenOwnerAndPreviewDelivered_CompletesCommissionAndPublishesEvent() {
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(43L, 105L)).thenReturn(Optional.of(deliveredPreview));
        stubHappyPathPersistence();
        Instant before = Instant.now();

        ApproveDeliverableCardResponse response = useCase.execute(105L, 501L);

        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.commissionStatus()).isEqualTo(CommissionStatus.COMPLETED);
        assertThat(response.approvedSubmissionId()).isEqualTo(43L);
        assertThat(response.versionNumber()).isEqualTo(2);
        assertThat(response.submissionStatus()).isEqualTo(CommissionSubmissionStatus.CUSTOMER_ACCEPTED);
        assertThat(response.completedAt()).isCloseTo(before, within(Duration.ofSeconds(5)));

        ArgumentCaptor<CommissionSubmissionEntity> submissionCaptor = ArgumentCaptor.forClass(CommissionSubmissionEntity.class);
        verify(commissionSubmissionRepository).save(submissionCaptor.capture());
        assertThat(submissionCaptor.getValue().getStatus()).isEqualTo(CommissionSubmissionStatus.CUSTOMER_ACCEPTED);

        ArgumentCaptor<CommissionRequestEntity> commissionCaptor = ArgumentCaptor.forClass(CommissionRequestEntity.class);
        verify(commissionRequestRepository).save(commissionCaptor.capture());
        CommissionRequestEntity saved = commissionCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(CommissionStatus.COMPLETED);
        assertThat(saved.getApprovedSubmissionId()).isEqualTo(43L);
        assertThat(saved.getAutoCompleteAt()).isNull();

        ArgumentCaptor<CommissionCompletedEvent> eventCaptor = ArgumentCaptor.forClass(CommissionCompletedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CommissionCompletedEvent event = eventCaptor.getValue();
        assertThat(event.commissionId()).isEqualTo(105L);
        assertThat(event.customerId()).isEqualTo(501L);
        assertThat(event.approvedSubmissionId()).isEqualTo(43L);
        assertThat(event.assignedStaffId()).isEqualTo(3L);
        assertThat(event.assignedDesignerId()).isEqualTo(5L);
        assertThat(event.source()).isEqualTo(CommissionCompletionSource.CUSTOMER_APPROVED);
        assertThat(event.completedAt()).isEqualTo(response.completedAt());
    }

    @Test
    @DisplayName("UTCID02 [N]: Approval inserts and broadcasts a SYSTEM chat message")
    void execute_WhenApproved_InsertsAndBroadcastsSystemMessage() {
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(43L, 105L)).thenReturn(Optional.of(deliveredPreview));
        stubHappyPathPersistence();

        ApproveDeliverableCardResponse response = useCase.execute(105L, 501L);

        ArgumentCaptor<CommissionMessageEntity> messageCaptor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(commissionMessageRepository).save(messageCaptor.capture());
        CommissionMessageEntity message = messageCaptor.getValue();
        assertThat(message.getCommissionId()).isEqualTo(105L);
        assertThat(message.getSenderRole()).isEqualTo(CommissionMessageSenderRole.SYSTEM);
        assertThat(message.getMessageType()).isEqualTo(CommissionMessageType.SYSTEM);
        assertThat(message.getContent()).contains("version 2");

        ArgumentCaptor<CommissionMessageResponse> broadcastCaptor = ArgumentCaptor.forClass(CommissionMessageResponse.class);
        verify(chatBroadcastPort).broadcastMessage(eq(105L), broadcastCaptor.capture());
        assertThat(broadcastCaptor.getValue().messageId()).isEqualTo(1301L);
        assertThat(response.systemMessage().messageId()).isEqualTo(1301L);
    }

    @Test
    @DisplayName("UTCID03 [B]: Legacy row without approved_submission_id falls back to latest STAFF_APPROVED submission")
    void execute_WhenApprovedSubmissionIdMissing_FallsBackToLatestStaffApproved() {
        commission.setApprovedSubmissionId(null);
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findTopByCommissionIdAndStatusOrderByVersionNumberDesc(105L, CommissionSubmissionStatus.STAFF_APPROVED))
                .thenReturn(Optional.of(deliveredPreview));
        stubHappyPathPersistence();

        ApproveDeliverableCardResponse response = useCase.execute(105L, 501L);

        assertThat(response.approvedSubmissionId()).isEqualTo(43L);
        assertThat(commission.getApprovedSubmissionId()).isEqualTo(43L);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.COMPLETED);
    }

    @Test
    @DisplayName("UTCID04 [A]: Commission not found -> CommissionNotFoundException, no side effects")
    void execute_WhenCommissionNotFound_Throws() {
        when(commissionRequestRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(999L, 501L))
                .isInstanceOf(CommissionNotFoundException.class);

        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort, eventPublisher);
    }

    @Test
    @DisplayName("UTCID05 [A]: AC01 - Non-owning customer -> CommissionOwnershipMismatchException, no side effects")
    void execute_WhenCallerIsNotOwner_ThrowsOwnershipMismatch() {
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> useCase.execute(105L, 777L))
                .isInstanceOf(CommissionOwnershipMismatchException.class);

        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort, eventPublisher);
    }

    @ParameterizedTest(name = "status {0}")
    @EnumSource(value = CommissionStatus.class, names = "PREVIEW_DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("UTCID06 [A]: Commission not in PREVIEW_DELIVERED (incl. already COMPLETED) -> InvalidCommissionStatusTransitionException")
    void execute_WhenStatusIsNotPreviewDelivered_Throws(CommissionStatus status) {
        commission.setStatus(status);
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("PREVIEW_DELIVERED");

        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionSubmissionRepository, commissionMessageRepository, chatBroadcastPort, eventPublisher);
    }

    @Test
    @DisplayName("UTCID07 [A]: Referenced submission is not STAFF_APPROVED -> InvalidCommissionStatusTransitionException")
    void execute_WhenDeliveredSubmissionNotStaffApproved_Throws() {
        deliveredPreview.setStatus(CommissionSubmissionStatus.PENDING_STAFF_REVIEW);
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(43L, 105L)).thenReturn(Optional.of(deliveredPreview));

        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("no delivered preview");

        verify(commissionSubmissionRepository, never()).save(any());
        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionMessageRepository, chatBroadcastPort, eventPublisher);
    }

    @Test
    @DisplayName("UTCID08 [A]: Referenced submission missing -> InvalidCommissionStatusTransitionException")
    void execute_WhenDeliveredSubmissionMissing_Throws() {
        when(commissionRequestRepository.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissionSubmissionRepository.findBySubmissionIdAndCommissionId(43L, 105L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(105L, 501L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);

        verify(commissionRequestRepository, never()).save(any());
        verifyNoInteractions(commissionMessageRepository, chatBroadcastPort, eventPublisher);
    }
}
