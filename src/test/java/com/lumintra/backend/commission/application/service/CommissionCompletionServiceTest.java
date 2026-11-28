package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.event.CommissionCompletedEvent;
import com.lumintra.backend.commission.domain.model.CommissionCompletionSource;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.CommissionSubmissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommissionCompletionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");
    private static final long ID = 105L;

    private final ObjectMapper json = new ObjectMapper();
    private CommissionRequestRepository commissions;
    private CommissionSubmissionRepository submissions;
    private CommissionMessageRepository messages;
    private CommissionRevisionRequestRepository revisions;
    private CommissionChatBroadcastPort broadcast;
    private ApplicationEventPublisher events;
    private CommissionCompletionService service;

    private CommissionRequestEntity commission;
    private CommissionSubmissionEntity preview;
    private final List<CommissionMessageEntity> savedMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        commissions = mock(CommissionRequestRepository.class);
        submissions = mock(CommissionSubmissionRepository.class);
        messages = mock(CommissionMessageRepository.class);
        revisions = mock(CommissionRevisionRequestRepository.class);
        broadcast = mock(CommissionChatBroadcastPort.class);
        events = mock(ApplicationEventPublisher.class);
        service = new CommissionCompletionService(commissions, submissions, messages, revisions,
                new CommissionMessageMapper(), broadcast, events, json);

        commission = CommissionRequestEntity.builder()
                .commissionId(ID).customerId(501L).assignedStaffId(3L).assignedDesignerId(5L)
                .status(CommissionStatus.PREVIEW_DELIVERED).approvedSubmissionId(43L)
                .autoCompleteAt(NOW.minusSeconds(60)).build();
        preview = CommissionSubmissionEntity.builder()
                .submissionId(43L).commissionId(ID).versionNumber(2)
                .status(CommissionSubmissionStatus.STAFF_APPROVED).build();
        when(submissions.findBySubmissionIdAndCommissionId(43L, ID)).thenReturn(Optional.of(preview));
        when(submissions.save(any(CommissionSubmissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commissions.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(CommissionMessageEntity.class))).thenAnswer(inv -> {
            CommissionMessageEntity message = inv.getArgument(0);
            if (message.getMessageId() == null) {
                message.setMessageId(1300L + savedMessages.size());
            }
            message.setCreatedAt(NOW);
            savedMessages.add(message);
            return message;
        });
    }

    private CommissionMessageEntity revisionCard(String metadataJson) {
        CommissionMessageEntity card = CommissionMessageEntity.create(ID, 3L, CommissionMessageSenderRole.STAFF,
                CommissionMessageType.EXTRA_FEE_CARD, "Revision brief", null, metadataJson);
        card.setMessageId(900L);
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(ID, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
        return card;
    }

    private CommissionRevisionRequestEntity pendingRevision() {
        CommissionRevisionRequestEntity revision = CommissionRevisionRequestEntity.open(
                ID, 900L, 2, "Make the cat blink", false, new BigDecimal("35000.00"));
        when(revisions.findAllByCommissionIdAndStatus(ID, CommissionRevisionRequestStatus.PENDING))
                .thenReturn(List.of(revision));
        return revision;
    }

    @Test
    @DisplayName("CMS-27 AC02 [N]: auto-completion accepts the preview, completes, posts the SYSTEM message and publishes AUTO_COMPLETED")
    void autoCompletionMatchesCustomerApprovalWithAutoSource() {
        CommissionCompletionService.Completion result =
                service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW);

        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.COMPLETED);
        assertThat(commission.getApprovedSubmissionId()).isEqualTo(43L);
        assertThat(commission.getAutoCompleteAt()).isNull();
        assertThat(preview.getStatus()).isEqualTo(CommissionSubmissionStatus.CUSTOMER_ACCEPTED);
        assertThat(result.completedAt()).isEqualTo(NOW);
        assertThat(result.systemMessage().content()).isEqualTo(
                "Không có phản hồi trong vòng 7 ngày — bản xem trước phiên bản 2 đã được tự động chấp nhận. Yêu cầu thiết kế hiện đã hoàn tất.");

        ArgumentCaptor<CommissionCompletedEvent> event = ArgumentCaptor.forClass(CommissionCompletedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new CommissionCompletedEvent(
                ID, 501L, 43L, 3L, 5L, CommissionCompletionSource.AUTO_COMPLETED, NOW));
        verify(broadcast).broadcastMessage(eq(ID), any(CommissionMessageResponse.class));
    }

    @Test
    @DisplayName("CMS-27 AC04 [N]: a pending revision brief is superseded and its card is expired and re-broadcast")
    void autoCompletionExpiresPendingRevisionBrief() throws Exception {
        CommissionRevisionRequestEntity revision = pendingRevision();
        CommissionMessageEntity card = revisionCard("{\"brief\":\"Make the cat blink\",\"status\":\"PENDING\"}");

        service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW);

        assertThat(revision.getStatus()).isEqualTo(CommissionRevisionRequestStatus.SUPERSEDED);
        verify(revisions).save(revision);
        assertThat(json.readTree(card.getMetadataJson()).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(json.readTree(card.getMetadataJson()).path("brief").asText()).isEqualTo("Make the cat blink");
        verify(messages).save(card);
        verify(broadcast, times(2)).broadcastMessage(eq(ID), any(CommissionMessageResponse.class));
    }

    @Test
    @DisplayName("CMS-27 AC04 [B]: a card posted without a status is treated as pending and expired")
    void legacyCardWithoutStatusIsExpired() throws Exception {
        CommissionMessageEntity card = revisionCard("{\"brief\":\"Older brief\"}");

        service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW);

        assertThat(json.readTree(card.getMetadataJson()).path("status").asText()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("CMS-27 AC04 [A]: a card that is already CONFIRMED (previous revision cycle) is left alone")
    void confirmedCardIsLeftUnchanged() {
        String metadata = "{\"status\":\"CONFIRMED\"}";
        CommissionMessageEntity card = revisionCard(metadata);

        service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW);

        assertThat(card.getMetadataJson()).isEqualTo(metadata);
        verify(messages, never()).save(card);
        verify(broadcast, times(1)).broadcastMessage(eq(ID), any(CommissionMessageResponse.class));
    }

    @Test
    @DisplayName("CMS-18 [N]: customer approval keeps its own message and never touches revision briefs")
    void customerApprovalLeavesRevisionBriefsAlone() {
        CommissionCompletionService.Completion result =
                service.complete(commission, CommissionCompletionSource.CUSTOMER_APPROVED, NOW);

        assertThat(result.systemMessage().content())
                .isEqualTo("Khách hàng đã phê duyệt bản xem trước phiên bản 2. Yêu cầu thiết kế hiện đã hoàn tất.");
        verifyNoInteractions(revisions);
        verify(messages, never()).findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(any(), any());
    }

    @Test
    @DisplayName("CMS-27 AC05 [A]: no STAFF_APPROVED preview fails without side effects")
    void missingDeliveredPreviewFails() {
        preview.setStatus(CommissionSubmissionStatus.CUSTOMER_ACCEPTED);
        pendingRevision();

        assertThatThrownBy(() -> service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("no delivered preview");
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        verify(revisions, never()).save(any());
        verifyNoInteractions(events, broadcast);
    }

    @Test
    @DisplayName("[A]: a commission that is not PREVIEW_DELIVERED cannot be completed")
    void wrongStatusFails() {
        commission.setStatus(CommissionStatus.AWAITING_EXTRA_PAYMENT);

        assertThatThrownBy(() -> service.complete(commission, CommissionCompletionSource.AUTO_COMPLETED, NOW))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(submissions, events);
    }
}
