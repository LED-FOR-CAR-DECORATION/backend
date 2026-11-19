package com.lumintra.backend.commission.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.RevisionRequestFakes;
import com.lumintra.backend.commission.api.request.InputRevisionEstimateRequest;
import com.lumintra.backend.commission.application.service.CommissionRevisionEstimateService;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.model.RevisionEstimateSource;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

class InputRevisionEstimateUseCaseTest {

    private final CommissionRequestRepository commissions = mock(CommissionRequestRepository.class);
    private final CommissionMessageRepository messages = mock(CommissionMessageRepository.class);
    private final CommissionChatBroadcastPort broadcast = mock(CommissionChatBroadcastPort.class);
    private final ObjectMapper json = new ObjectMapper();
    private final RevisionRequestFakes revisions = new RevisionRequestFakes();
    private final InputRevisionEstimateUseCase useCase = new InputRevisionEstimateUseCase(
            commissions,
            new CommissionRevisionEstimateService(
                    commissions, revisions.repository, messages, new CommissionMessageMapper(), broadcast, json));

    private CommissionRequestEntity commission;
    private CommissionRevisionRequestEntity revision;

    @BeforeEach
    void setUp() {
        commission = CommissionRequestEntity.builder()
                .commissionId(105L)
                .customerId(501L)
                .assignedStaffId(3L)
                .assignedDesignerId(5L)
                .initialPrice(new BigDecimal("350000"))
                .totalPrice(new BigDecimal("350000"))
                .revisionsUsed(1)
                .maxRevisions(2)
                .status(CommissionStatus.AWAITING_ESTIMATE)
                .slaDueAt(Instant.now().minus(Duration.ofHours(2)))
                .isSlaOverdue(true)
                .build();
        revision = revisions.repository.save(
                CommissionRevisionRequestEntity.open(105L, 402L, 2, "Brighter outline", true, BigDecimal.ZERO));
        revision.confirmFree(2, Instant.now());
        when(commissions.findByIdForUpdate(105L)).thenReturn(Optional.of(commission));
        when(commissions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void designerEstimateStartsRevisionAndSetsBothDeadlines() throws Exception {
        Instant before = Instant.now();

        var response = useCase.execute(105L, 5L, new InputRevisionEstimateRequest(36));

        Instant designerDue = commission.getSlaDueAt();
        assertThat(designerDue).isCloseTo(before.plus(Duration.ofHours(36)), within(Duration.ofSeconds(5)));
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(commission.isSlaOverdue()).isFalse();

        assertThat(revision.getStatus()).isEqualTo(CommissionRevisionRequestStatus.IN_PROGRESS);
        assertThat(revision.getEstimatedHours()).isEqualTo(36);
        assertThat(revision.getEstimateSource()).isEqualTo(RevisionEstimateSource.DESIGNER);
        assertThat(revision.getDesignerDueAt()).isEqualTo(designerDue);
        assertThat(revision.getCustomerDueAt()).isEqualTo(designerDue.plus(Duration.ofHours(24)));

        assertThat(response.commissionId()).isEqualTo(105L);
        assertThat(response.status()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(response.revisionRequestId()).isEqualTo(revision.getRevisionRequestId());
        assertThat(response.revisionNumber()).isEqualTo(2);
        assertThat(response.estimatedHours()).isEqualTo(36);
        assertThat(response.slaDueAt()).isEqualTo(designerDue);
        assertThat(response.customerDeliveryDeadline()).isEqualTo(designerDue.plus(Duration.ofHours(24)));

        var captor = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(messages).save(captor.capture());
        var message = captor.getValue();
        assertThat(message.getSenderRole()).isEqualTo(CommissionMessageSenderRole.SYSTEM);
        assertThat(message.getMessageType()).isEqualTo(CommissionMessageType.SYSTEM);
        assertThat(message.getContent()).startsWith("Revision #2 is now in progress. Expected delivery by ");
        var metadata = json.readTree(message.getMetadataJson());
        assertThat(metadata.path("event").asText()).isEqualTo("REVISION_ESTIMATED");
        assertThat(metadata.path("estimatedDeliveryAt").asText())
                .isEqualTo(revision.getCustomerDueAt().toString());
        verify(broadcast).broadcastMessage(eq(105L), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 72})
    void acceptsEstimateBoundaries(int hours) {
        useCase.execute(105L, 5L, new InputRevisionEstimateRequest(hours));
        assertThat(revision.getEstimatedHours()).isEqualTo(hours);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.IN_PROGRESS);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 73})
    void rejectsEstimateOutsideBusinessRuleRange(int hours) {
        assertThatThrownBy(() -> useCase.execute(105L, 5L, new InputRevisionEstimateRequest(hours)))
                .isInstanceOf(IllegalArgumentException.class);
        assertUnchanged();
    }

    @Test
    void rejectsMissingCommission() {
        when(commissions.findByIdForUpdate(106L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.execute(106L, 5L, new InputRevisionEstimateRequest(24)))
                .isInstanceOf(CommissionNotFoundException.class);
    }

    @Test
    void rejectsOtherOrUnassignedDesigner() {
        assertThatThrownBy(() -> useCase.execute(105L, 6L, new InputRevisionEstimateRequest(24)))
                .isInstanceOf(CommissionOwnershipMismatchException.class);
        commission.setAssignedDesignerId(null);
        assertThatThrownBy(() -> useCase.execute(105L, 5L, new InputRevisionEstimateRequest(24)))
                .isInstanceOf(CommissionOwnershipMismatchException.class);
        assertUnchanged();
    }

    @Test
    void rejectsCommissionNotAwaitingEstimate() {
        commission.setStatus(CommissionStatus.IN_PROGRESS);
        assertThatThrownBy(() -> useCase.execute(105L, 5L, new InputRevisionEstimateRequest(24)))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        assertThat(revision.getStatus()).isEqualTo(CommissionRevisionRequestStatus.CONFIRMED);
        verify(messages, never()).save(any());
    }

    @Test
    void rejectsWhenNoConfirmedRevisionIsWaiting() {
        revision.supersede();
        assertThatThrownBy(() -> useCase.execute(105L, 5L, new InputRevisionEstimateRequest(24)))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_ESTIMATE);
        verify(messages, never()).save(any());
    }

    private void assertUnchanged() {
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.AWAITING_ESTIMATE);
        assertThat(revision.getStatus()).isEqualTo(CommissionRevisionRequestStatus.CONFIRMED);
        verify(commissions, never()).save(any());
        verify(messages, never()).save(any());
    }
}
