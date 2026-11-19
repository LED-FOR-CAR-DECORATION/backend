package com.lumintra.backend.commission.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.model.CommissionRevisionEstimatePolicy;
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
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Applies a revision delivery estimate (BR-FL3-13, BR-FL3-14). Shared by the designer endpoint
 * (CMS-24) and the default-estimate scheduler (CMS-28) so both paths set the same deadlines.
 * Callers must hold the commission row lock and run inside a transaction.
 */
@Service
@RequiredArgsConstructor
public class CommissionRevisionEstimateService {

    private static final Logger log = LoggerFactory.getLogger(CommissionRevisionEstimateService.class);
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy '(GMT+7)'").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final CommissionRequestRepository commissions;
    private final CommissionRevisionRequestRepository revisionRequests;
    private final CommissionMessageRepository messages;
    private final CommissionMessageMapper messageMapper;
    private final CommissionChatBroadcastPort broadcast;
    private final ObjectMapper json;

    public AppliedEstimate apply(
            CommissionRequestEntity commission,
            int estimatedHours,
            RevisionEstimateSource source,
            Instant now) {
        if (commission.getStatus() != CommissionStatus.AWAITING_ESTIMATE) {
            throw new InvalidCommissionStatusTransitionException(
                    "Revision estimates require AWAITING_ESTIMATE status (was " + commission.getStatus() + ").");
        }
        if (!CommissionRevisionEstimatePolicy.isValidEstimate(estimatedHours)) {
            throw new IllegalArgumentException("Estimate must be between "
                    + CommissionRevisionEstimatePolicy.MIN_ESTIMATE_HOURS + " and "
                    + CommissionRevisionEstimatePolicy.MAX_ESTIMATE_HOURS + " hours.");
        }
        CommissionRevisionRequestEntity revision = revisionRequests
                .findFirstByCommissionIdAndStatusInOrderByRevisionRequestIdDesc(
                        commission.getCommissionId(), List.of(CommissionRevisionRequestStatus.CONFIRMED))
                .orElseThrow(() -> new InvalidCommissionStatusTransitionException(
                        "No confirmed revision request is waiting for an estimate."));

        Instant designerDueAt = CommissionRevisionEstimatePolicy.designerDueAt(now, estimatedHours);
        Instant customerDueAt = CommissionRevisionEstimatePolicy.customerDueAt(designerDueAt);
        revision.startWork(estimatedHours, source, now, designerDueAt, customerDueAt);
        commission.startRevisionWork(designerDueAt);
        CommissionRevisionRequestEntity savedRevision = revisionRequests.save(revision);
        CommissionRequestEntity savedCommission = commissions.save(commission);

        CommissionMessageEntity message = messages.save(CommissionMessageEntity.create(
                commission.getCommissionId(),
                null,
                CommissionMessageSenderRole.SYSTEM,
                CommissionMessageType.SYSTEM,
                "Revision #" + savedRevision.getRevisionNumber() + " is now in progress. Expected delivery by "
                        + DISPLAY_FORMAT.format(customerDueAt) + ".",
                null,
                metadata(savedRevision)));
        broadcast.broadcastMessage(commission.getCommissionId(), messageMapper.toResponse(message));

        log.info("Revision estimate applied [commissionId={}] [revisionRequestId={}] [estimatedHours={}] [source={}]"
                        + " [designerDueAt={}] [customerDueAt={}]",
                commission.getCommissionId(), savedRevision.getRevisionRequestId(), estimatedHours, source,
                designerDueAt, customerDueAt);
        return new AppliedEstimate(savedCommission, savedRevision);
    }

    private String metadata(CommissionRevisionRequestEntity revision) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("event", "REVISION_ESTIMATED");
        metadata.put("revisionRequestId", revision.getRevisionRequestId());
        metadata.put("revisionNumber", revision.getRevisionNumber());
        metadata.put("estimatedDeliveryAt", revision.getCustomerDueAt().toString());
        try {
            return json.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize revision estimate message.", ex);
        }
    }

    public record AppliedEstimate(CommissionRequestEntity commission, CommissionRevisionRequestEntity revision) {
    }
}
