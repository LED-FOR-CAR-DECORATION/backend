package com.lumintra.backend.commission.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commerce.domain.service.CustomerSubscriptionBonusLookupService;
import com.lumintra.backend.commission.api.request.PostRevisionCardRequest;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.application.service.CommissionRevisionRequestRecorder;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class PostRevisionCardUseCase {
    private static final Logger log = LoggerFactory.getLogger(PostRevisionCardUseCase.class);
    private static final BigDecimal EXTRA_FEE_RATE = new BigDecimal("0.10");

    private final CommissionRequestRepository commissions;
    private final CommissionMessageRepository messages;
    private final CommissionMessageMapper mapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;
    private final CommissionRevisionRequestRecorder revisionRequests;
    private final CustomerSubscriptionBonusLookupService subscriptionBonusLookupService;

    @Transactional
    public CommissionMessageResponse execute(
            Long id, Long staffId, PostRevisionCardRequest request) {
        var commission =
                commissions
                        .findForRevisionCard(id)
                        .orElseThrow(
                                () ->
                                        new CommissionNotFoundException(
                                                "Commission request not found."));
        if (staffId == null || !staffId.equals(commission.getAssignedStaffId())) {
            throw new CommissionStaffLockException(
                    "Only the assigned staff member can post a revision card.");
        }
        if (commission.getStatus() != CommissionStatus.PREVIEW_DELIVERED) {
            throw new InvalidCommissionStatusTransitionException(
                    "Revision cards require PREVIEW_DELIVERED status.");
        }

        boolean usesCommissionQuota = commission.getRevisionsUsed() < commission.getMaxRevisions();
        int sharedWalletCredits =
                usesCommissionQuota
                        ? 0
                        : Math.max(
                                0,
                                subscriptionBonusLookupService.findExtraCommissionRevisions(
                                        commission.getCustomerId()));
        boolean usesSubscriptionCredit = !usesCommissionQuota && sharedWalletCredits > 0;
        boolean usesFreeQuota = usesCommissionQuota || usesSubscriptionCredit;
        var fee =
                usesFreeQuota
                        ? BigDecimal.ZERO.setScale(2)
                        : commission
                                .getInitialPrice()
                                .multiply(EXTRA_FEE_RATE)
                                .setScale(2, RoundingMode.HALF_UP);
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("brief", request.brief());
        metadata.put("usesFreeQuota", usesFreeQuota);
        metadata.put("usesSubscriptionCredit", usesSubscriptionCredit);
        metadata.put("revisionFee", fee);
        metadata.put("feePercentage", usesFreeQuota ? 0 : 10);
        metadata.put("revisionsUsed", commission.getRevisionsUsed());
        metadata.put("maxRevisions", commission.getMaxRevisions());
        String payload;
        try {
            payload = json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize revision card.", ex);
        }

        var saved =
                messages.save(
                        CommissionMessageEntity.create(
                                id,
                                staffId,
                                CommissionMessageSenderRole.STAFF,
                                CommissionMessageType.EXTRA_FEE_CARD,
                                request.brief(),
                                null,
                                payload));
        var revision =
                revisionRequests.open(
                        saved,
                        commission.getRevisionsUsed() + 1,
                        request.brief(),
                        usesFreeQuota,
                        fee);
        var response = mapper.toResponse(saved);
        broadcast.broadcastMessage(id, response);
        log.info(
                "Revision card posted [commissionId={}] [messageId={}] [revisionRequestId={}]"
                        + " [usesFreeQuota={}]",
                id,
                saved.getMessageId(),
                revision.getRevisionRequestId(),
                usesFreeQuota);
        return response;
    }
}
