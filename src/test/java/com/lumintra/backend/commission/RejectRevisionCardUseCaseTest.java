package com.lumintra.backend.commission;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.application.RejectRevisionCardUseCase;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.*;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.*;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.*;
import com.lumintra.backend.shared.exception.commission.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class RejectRevisionCardUseCaseTest {
    @Mock CommissionRequestRepository commissions;
    @Mock CommissionMessageRepository messages;
    @Mock CommissionChatBroadcastPort broadcast;
    @Spy CommissionMessageMapper mapper = new CommissionMessageMapper();
    @Spy ObjectMapper json = new ObjectMapper();
    RevisionRequestFakes revisions = new RevisionRequestFakes();
    @Spy CommissionRevisionRequestRecorder revisionRequests = revisions.recorder;
    @InjectMocks RejectRevisionCardUseCase useCase;
    CommissionRequestEntity commission;
    CommissionMessageEntity card;

    @BeforeEach
    void setup() {
        commission =
                CommissionRequestEntity.builder()
                        .commissionId(105L)
                        .customerId(501L)
                        .initialPrice(new BigDecimal("350000.00"))
                        .totalPrice(new BigDecimal("350000.00"))
                        .revisionsUsed(0)
                        .maxRevisions(2)
                        .status(CommissionStatus.PREVIEW_DELIVERED)
                        .autoCompleteAt(Instant.now().plusSeconds(3600))
                        .build();
        card =
                CommissionMessageEntity.create(
                        105L,
                        3L,
                        CommissionMessageSenderRole.STAFF,
                        CommissionMessageType.EXTRA_FEE_CARD,
                        "Wrong star color",
                        null,
                        "{\"brief\":\"Wrong star color\",\"revisionsUsed\":0}");
        card.setMessageId(402L);
        when(commissions.findForRevisionConfirmation(105L)).thenReturn(Optional.of(commission));
    }

    private void availableCard() {
        when(messages.findFirstByCommissionIdAndMessageTypeOrderByMessageIdDesc(
                        105L, CommissionMessageType.EXTRA_FEE_CARD))
                .thenReturn(Optional.of(card));
    }

    @Test
    void rejectsPendingCardWithoutConsumingQuotaAndBroadcastsUpdatedCard() throws Exception {
        availableCard();
        when(messages.save(card)).thenReturn(card);

        var response = useCase.execute(105L, 501L, 402L, "  Sai màu ngôi sao  ");

        assertThat(response.status()).isEqualTo(CommissionStatus.PREVIEW_DELIVERED);
        assertThat(response.messageId()).isEqualTo(402L);
        assertThat(commission.getRevisionsUsed()).isEqualTo(0);
        assertThat(commission.getExtraRevisionFee()).isEqualByComparingTo("0.00");
        assertThat(revisions.only().getStatus()).isEqualTo(CommissionRevisionRequestStatus.REJECTED);

        var tree = json.readTree(card.getMetadataJson());
        assertThat(tree.path("status").asText()).isEqualTo("REJECTED");
        assertThat(tree.path("rejectedReason").asText()).isEqualTo("Sai màu ngôi sao");
        verify(broadcast).broadcastMessage(eq(105L), any());
    }

    @Test
    void rejectsAlreadyProcessedCard() {
        availableCard();
        card.setMetadataJson("{\"brief\":\"Wrong star color\",\"status\":\"CONFIRMED\"}");

        assertThatThrownBy(() -> useCase.execute(105L, 501L, 402L, null))
                .isInstanceOf(InvalidCommissionStatusTransitionException.class);
        verifyNoInteractions(broadcast);
    }

    @Test
    void rejectsNonOwnerCustomer() {
        assertThatThrownBy(() -> useCase.execute(105L, 999L, 402L, null))
                .isInstanceOf(CommissionOwnershipMismatchException.class);
        verifyNoInteractions(broadcast);
    }
}
