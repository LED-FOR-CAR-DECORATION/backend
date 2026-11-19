package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Keeps {@code commission_revision_request} in step with the {@code EXTRA_FEE_CARD} chat card
 * that presents the brief to the customer.
 */
@Service
@RequiredArgsConstructor
public class CommissionRevisionRequestRecorder {

    private final CommissionRevisionRequestRepository revisionRequests;

    /** CMS-19: record a newly posted brief, superseding briefs the customer never confirmed. */
    public CommissionRevisionRequestEntity open(
            CommissionMessageEntity card,
            int revisionNumber,
            String brief,
            boolean usesFreeQuota,
            BigDecimal fee) {
        for (var stale :
                revisionRequests.findAllByCommissionIdAndStatus(
                        card.getCommissionId(), CommissionRevisionRequestStatus.PENDING)) {
            stale.supersede();
            revisionRequests.save(stale);
        }
        return revisionRequests.save(
                CommissionRevisionRequestEntity.open(
                        card.getCommissionId(),
                        card.getMessageId(),
                        revisionNumber,
                        brief,
                        usesFreeQuota,
                        fee));
    }

    /**
     * Returns the record behind a brief card. Cards posted before the table existed have none,
     * so one is rebuilt from the card's metadata.
     */
    public CommissionRevisionRequestEntity forCard(
            CommissionMessageEntity card, JsonNode metadata, int revisionNumber) {
        return revisionRequests
                .findByMessageId(card.getMessageId())
                .orElseGet(
                        () -> {
                            var brief = metadata.path("brief").asText(null);
                            var fee = metadata.path("revisionFee");
                            return CommissionRevisionRequestEntity.open(
                                    card.getCommissionId(),
                                    card.getMessageId(),
                                    revisionNumber,
                                    brief != null && !brief.isBlank() ? brief : card.getContent(),
                                    metadata.path("usesFreeQuota").asBoolean(false),
                                    fee.isNumber()
                                            ? fee.decimalValue()
                                            : BigDecimal.ZERO.setScale(2));
                        });
    }

    public CommissionRevisionRequestEntity save(CommissionRevisionRequestEntity revision) {
        return revisionRequests.save(revision);
    }
}
