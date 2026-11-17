package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission. InvalidCommissionStatusTransitionException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * UseCase implementing CMS-11: Listing chronological messages in a commission chat room.
 */
@Service
@RequiredArgsConstructor
public class ListCommissionMessagesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListCommissionMessagesUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionMessageMapper commissionMessageMapper;

    @Transactional(readOnly = true)
    public List<CommissionMessageResponse> execute(
            Long commissionId,
            Long callerId,
            CommissionMessageSenderRole callerRole) {

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Owning CUSTOMER or any STAFF/MANAGER/ADMIN
        if (callerRole == CommissionMessageSenderRole.CUSTOMER && !commission.isOwnedBy(callerId)) {
            log.warn("Commission chat history ownership mismatch [commissionId={}] [callerId={}] [ownerCustomerId={}]",
                    commissionId, callerId, commission.getCustomerId());
            throw new CommissionOwnershipMismatchException("You do not have permission to view this commission chat.");
        }

        // AC02: Return chronological messages once status is at least PAID (active chat states + COMPLETED)
        CommissionStatus status = commission.getStatus();
        if (status == null || (!status.isChatActive() && status != CommissionStatus.COMPLETED)) {
            throw new InvalidCommissionStatusTransitionException(
                    "Commission chat history is not available in status: " + status
            );
        }

        return commissionMessageRepository.findAllByCommissionIdOrderByCreatedAtAsc(commissionId)
                .stream()
                .map(commissionMessageMapper::toResponse)
                .toList();
    }
}