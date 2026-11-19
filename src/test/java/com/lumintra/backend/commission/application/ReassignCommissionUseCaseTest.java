package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.ReassignCommissionRequest;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.api.response.ReassignCommissionResponse;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.domain.service.EmployeeDirectoryService;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionReassignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidDesignerAssignmentException;
import com.lumintra.backend.shared.exception.commission.InvalidStaffAssignmentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReassignCommissionUseCaseTest {

    private static final long ID = 105L;
    private static final Instant OLD_DEADLINE = Instant.now().minus(Duration.ofHours(5));

    private CommissionRequestRepository commissions;
    private EmployeeDirectoryService directory;
    private CommissionMessageRepository messages;
    private CommissionChatBroadcastPort broadcast;
    private ReassignCommissionUseCase useCase;

    @BeforeEach
    void setUp() {
        commissions = mock(CommissionRequestRepository.class);
        directory = mock(EmployeeDirectoryService.class);
        messages = mock(CommissionMessageRepository.class);
        broadcast = mock(CommissionChatBroadcastPort.class);
        useCase = new ReassignCommissionUseCase(commissions, directory, messages, new CommissionMessageMapper(), broadcast);
        when(commissions.save(any(CommissionRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messages.save(any(CommissionMessageEntity.class))).thenAnswer(inv -> {
            CommissionMessageEntity message = inv.getArgument(0);
            message.setMessageId(1500L);
            message.setCreatedAt(Instant.now());
            return message;
        });
        when(directory.isStaff(4L)).thenReturn(true);
        when(directory.isDesigner(6L)).thenReturn(true);
    }

    private CommissionRequestEntity commission(CommissionStatus status, Long staffId, Long designerId) {
        CommissionRequestEntity entity = CommissionRequestEntity.builder()
                .commissionId(ID).customerId(501L).status(status)
                .assignedStaffId(staffId).assignedDesignerId(designerId)
                .slaDueAt(OLD_DEADLINE).isSlaOverdue(true)
                .build();
        when(commissions.findByIdForUpdate(ID)).thenReturn(Optional.of(entity));
        return entity;
    }

    private static ReassignCommissionRequest request(Long staffId, Long designerId) {
        return new ReassignCommissionRequest(staffId, designerId, " Designer on leave ");
    }

    private String postedChatText() {
        ArgumentCaptor<CommissionMessageEntity> message = ArgumentCaptor.forClass(CommissionMessageEntity.class);
        verify(messages).save(message.capture());
        verify(broadcast).broadcastMessage(eq(ID), any(CommissionMessageResponse.class));
        return message.getValue().getContent();
    }

    // ---------------------------------------------------------------- designer, AC05 deadlines

    @Test
    @DisplayName("AC05 [N]: new designer while IN_PROGRESS gets now + 72h and the overdue flag cleared")
    void designerInProgressGetsFreshDraftDeadline() {
        CommissionRequestEntity entity = commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        ReassignCommissionResponse response = useCase.execute(ID, request(null, 6L), 2L);

        assertThat(entity.getAssignedDesignerId()).isEqualTo(6L);
        assertThat(entity.getAssignedStaffId()).isEqualTo(3L);
        assertThat(entity.getStatus()).isEqualTo(CommissionStatus.IN_PROGRESS);
        assertThat(entity.getSlaDueAt()).isCloseTo(Instant.now().plus(Duration.ofHours(72)), within(Duration.ofSeconds(5)));
        assertThat(entity.isSlaOverdue()).isFalse();
        assertThat(response.assignedDesignerId()).isEqualTo(6L);
        assertThat(response.isSlaOverdue()).isFalse();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(postedChatText()).isEqualTo("This commission has been reassigned to a new designer.");
    }

    @Test
    @DisplayName("AC05 [N]: new designer while INTERNAL_REVISION_REQUIRED also gets now + 72h")
    void designerInRevisionRequiredGetsFreshDraftDeadline() {
        CommissionRequestEntity entity = commission(CommissionStatus.INTERNAL_REVISION_REQUIRED, 3L, 5L);

        useCase.execute(ID, request(null, 6L), 2L);

        assertThat(entity.getSlaDueAt()).isCloseTo(Instant.now().plus(Duration.ofHours(72)), within(Duration.ofSeconds(5)));
        assertThat(entity.isSlaOverdue()).isFalse();
    }

    @Test
    @DisplayName("AC05 [N]: new designer while AWAITING_ESTIMATE gets a fresh 24h estimate window")
    void designerAwaitingEstimateGetsFreshEstimateWindow() {
        CommissionRequestEntity entity = commission(CommissionStatus.AWAITING_ESTIMATE, 3L, 5L);

        useCase.execute(ID, request(null, 6L), 2L);

        assertThat(entity.getSlaDueAt()).isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(Duration.ofSeconds(5)));
        assertThat(entity.isSlaOverdue()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = CommissionStatus.class, names = {"INTERNAL_REVIEW", "PREVIEW_DELIVERED", "AWAITING_EXTRA_PAYMENT"})
    @DisplayName("AC05 [B]: in other designer statuses the deadline and flag are unchanged")
    void designerOtherStatusesKeepDeadline(CommissionStatus status) {
        CommissionRequestEntity entity = commission(status, 3L, 5L);

        useCase.execute(ID, request(null, 6L), 2L);

        assertThat(entity.getAssignedDesignerId()).isEqualTo(6L);
        assertThat(entity.getSlaDueAt()).isEqualTo(OLD_DEADLINE);
        assertThat(entity.isSlaOverdue()).isTrue();
        assertThat(entity.getStatus()).isEqualTo(status);
    }

    // ---------------------------------------------------------------- staff

    @Test
    @DisplayName("AC02 [N]: staff reassignment keeps the designer deadline")
    void staffReassignmentKeepsDeadline() {
        CommissionRequestEntity entity = commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        useCase.execute(ID, request(4L, null), 2L);

        assertThat(entity.getAssignedStaffId()).isEqualTo(4L);
        assertThat(entity.getAssignedDesignerId()).isEqualTo(5L);
        assertThat(entity.getSlaDueAt()).isEqualTo(OLD_DEADLINE);
        assertThat(postedChatText()).isEqualTo("This commission has been reassigned to a new staff member.");
    }

    @Test
    @DisplayName("AC03 [N]: staff can be reassigned before payment, without a chat message (chat not open yet)")
    void staffBeforePaymentPostsNoMessage() {
        CommissionRequestEntity entity = commission(CommissionStatus.PENDING_REVIEW, 3L, null);

        useCase.execute(ID, request(4L, null), 2L);

        assertThat(entity.getAssignedStaffId()).isEqualTo(4L);
        assertThat(entity.getStatus()).isEqualTo(CommissionStatus.PENDING_REVIEW);
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    @DisplayName("AC03 [B]: a PAID commission with no staff yet can get one")
    void staffOnPaidWithoutStaff() {
        CommissionRequestEntity entity = commission(CommissionStatus.PAID, null, null);

        useCase.execute(ID, request(4L, null), 2L);

        assertThat(entity.getAssignedStaffId()).isEqualTo(4L);
    }

    @Test
    @DisplayName("AC06 [N]: staff and designer together get one combined message")
    void bothAtOnce() {
        CommissionRequestEntity entity = commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        useCase.execute(ID, request(4L, 6L), 2L);

        assertThat(entity.getAssignedStaffId()).isEqualTo(4L);
        assertThat(entity.getAssignedDesignerId()).isEqualTo(6L);
        assertThat(postedChatText()).isEqualTo("This commission has been reassigned to a new staff member and designer.");
    }

    // ---------------------------------------------------------------- rejections

    @Test
    @DisplayName("AC01 [A]: unknown commission is 404")
    void unknownCommission() {
        when(commissions.findByIdForUpdate(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(ID, request(4L, null), 2L))
                .isInstanceOf(CommissionNotFoundException.class);
    }

    @ParameterizedTest
    @EnumSource(value = CommissionStatus.class, names = {"COMPLETED", "CANCELLED", "EXPIRED"})
    @DisplayName("AC03 [A]: terminal commissions can't be reassigned")
    void terminalStatusesRejected(CommissionStatus status) {
        commission(status, 3L, 5L);

        assertThatThrownBy(() -> useCase.execute(ID, request(4L, null), 2L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verify(commissions, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = CommissionStatus.class, names = {"PENDING_REVIEW", "PENDING_PAYMENT", "PAID"})
    @DisplayName("AC03 [A]: no designer can be set before one was assigned (use CMS-12 for PAID)")
    void designerBeforeAssignmentRejected(CommissionStatus status) {
        commission(status, 3L, null);

        assertThatThrownBy(() -> useCase.execute(ID, request(null, 6L), 2L))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class)
                .hasMessageContaining("assign endpoint");
        verify(commissions, never()).save(any());
    }

    @Test
    @DisplayName("AC04 [A]: reassigning to the current staff or designer is 400")
    void noChangeRejected() {
        commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        assertThatThrownBy(() -> useCase.execute(ID, request(3L, null), 2L))
                .isInstanceOf(InvalidCommissionReassignmentException.class);
        assertThatThrownBy(() -> useCase.execute(ID, request(4L, 5L), 2L))
                .isInstanceOf(InvalidCommissionReassignmentException.class);
        verify(commissions, never()).save(any());
    }

    @Test
    @DisplayName("AC02 [A]: target must be an active STAFF or DESIGNER")
    void invalidTargetsRejected() {
        CommissionRequestEntity entity = commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        assertThatThrownBy(() -> useCase.execute(ID, request(9L, null), 2L))
                .isInstanceOf(InvalidStaffAssignmentException.class);
        assertThatThrownBy(() -> useCase.execute(ID, request(null, 9L), 2L))
                .isInstanceOf(InvalidDesignerAssignmentException.class);
        assertThat(entity.getAssignedStaffId()).isEqualTo(3L);
        assertThat(entity.getAssignedDesignerId()).isEqualTo(5L);
        verify(commissions, never()).save(any());
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    @DisplayName("AC06 [N]: the commission is loaded under the row lock")
    void usesRowLock() {
        commission(CommissionStatus.IN_PROGRESS, 3L, 5L);

        useCase.execute(ID, request(4L, null), 2L);

        verify(commissions).findByIdForUpdate(ID);
        verify(commissions, never()).findById(any());
    }
}
