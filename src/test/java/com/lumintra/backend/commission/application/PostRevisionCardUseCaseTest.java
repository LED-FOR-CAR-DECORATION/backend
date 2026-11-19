package com.lumintra.backend.commission.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.request.PostRevisionCardRequest;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

class PostRevisionCardUseCaseTest {
    private final CommissionRequestRepository commissions = mock(CommissionRequestRepository.class);
    private final CommissionMessageRepository messages = mock(CommissionMessageRepository.class);
    private final CommissionChatBroadcastPort broadcast = mock(CommissionChatBroadcastPort.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PostRevisionCardUseCase useCase =
            new PostRevisionCardUseCase(
                    commissions, messages, new CommissionMessageMapper(), broadcast, json);

    @Test
    void createsFreeCardWithoutConsumingQuotaOrChangingStatus() throws Exception {
        var commission = commission(1);
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = useCase.execute(105L, 3L, new PostRevisionCardRequest(" Brighter outline "));
        assertThat(response.messageType()).isEqualTo(CommissionMessageType.EXTRA_FEE_CARD);
        assertThat(response.content()).isEqualTo("Brighter outline");
        var metadata = json.readTree(response.metadataJson());
        assertThat(metadata.get("usesFreeQuota").asBoolean()).isTrue();
        assertThat(metadata.get("revisionFee").decimalValue()).isEqualByComparingTo("0");
        assertThat(commission.getRevisionsUsed()).isEqualTo(1);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        verify(commissions, never()).save(any());
        verify(broadcast).broadcastMessage(105L, response);
    }

    @Test
    void quotaBoundaryCalculatesTenPercentOfInitialPrice() throws Exception {
        var commission = commission(2);
        commission.setInitialPrice(new BigDecimal("123.45"));
        commission.setTotalPrice(new BigDecimal("999"));
        when(messages.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = useCase.execute(105L, 3L, new PostRevisionCardRequest("Change color"));
        var metadata = json.readTree(response.metadataJson());
        assertThat(metadata.get("usesFreeQuota").asBoolean()).isFalse();
        assertThat(metadata.get("revisionFee").decimalValue()).isEqualByComparingTo("12.35");
        assertThat(metadata.get("feePercentage").asInt()).isEqualTo(10);
        assertThat(commission.getStatus()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(commission.getRevisionsUsed()).isEqualTo(2);
        assertThat(commission.getTotalPrice()).isEqualByComparingTo("999");
    }

    @Test
    void rejectsOtherAndUnassignedStaff() {
        var commission = commission(0);
        assertThatThrownBy(() -> useCase.execute(105L, 4L, new PostRevisionCardRequest("Brief")))
                .isInstanceOf(CommissionStaffLockException.class);
        commission.setAssignedStaffId(null);
        assertThatThrownBy(() -> useCase.execute(105L, 3L, new PostRevisionCardRequest("Brief")))
                .isInstanceOf(CommissionStaffLockException.class);
        verifyNoInteractions(messages, broadcast);
    }

    @Test
    void rejectsMissingCommissionAndWrongStatus() {
        assertThatThrownBy(() -> useCase.execute(105L, 3L, new PostRevisionCardRequest("Brief")))
                .isInstanceOf(CommissionNotFoundException.class);
        commission(0).setStatus(CommissionStatus.INTERNAL_REVIEW);
        assertThatThrownBy(() -> useCase.execute(105L, 3L, new PostRevisionCardRequest("Brief")))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(messages, broadcast);
    }

    private CommissionRequestEntity commission(int used) {
        var commission =
                CommissionRequestEntity.builder()
                        .commissionId(105L)
                        .assignedStaffId(3L)
                        .status(CommissionStatus.PREVIEW_DELIVERED)
                        .revisionsUsed(used)
                        .maxRevisions(2)
                        .initialPrice(new BigDecimal("350000"))
                        .build();
        when(commissions.findForRevisionCard(105L)).thenReturn(Optional.of(commission));
        return commission;
    }
}
