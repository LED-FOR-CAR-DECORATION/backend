package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.request.SendCommissionMessageRequest;
import com.lumintra.backend.commission.api.response.CommissionMessageResponse;
import com.lumintra.backend.commission.domain.model.CommissionMessageSenderRole;
import com.lumintra.backend.commission.domain.model.CommissionMessageType;
import com.lumintra.backend.commission.domain.service.CommissionChatBroadcastPort;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionMessageEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionMessageMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionMessageRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import com.lumintra.backend.media.domain.service.PrivateMediaReferenceValidator;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionOwnershipMismatchException;
import com.lumintra.backend.shared.exception.commission.CommissionStaffLockException;
import com.lumintra.backend.shared.exception.commission.InvalidCommissionStatusTransitionException;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class SendCommissionMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendCommissionMessageUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionMessageRepository commissionMessageRepository;
    private final CommissionMessageMapper commissionMessageMapper;
    private final CommissionChatBroadcastPort chatBroadcastPort;
    private final PrivateMediaReferenceValidator privateMediaReferenceValidator;

    @Transactional
    public CommissionMessageResponse execute(
            Long commissionId,
            Long callerId,
            CommissionMessageSenderRole senderRole,
            boolean isPrivilegedManagement,
            SendCommissionMessageRequest request) {

        if (callerId == null || callerId <= 0) {
            throw new InvalidRuleConfigurationException("Authenticated caller ID is required.");
        }
        if (senderRole != CommissionMessageSenderRole.CUSTOMER && senderRole != CommissionMessageSenderRole.STAFF) {
            throw new InvalidRuleConfigurationException("Only CUSTOMER or STAFF participants can send chat messages.");
        }

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> new CommissionNotFoundException("Commission request not found with id: " + commissionId));

        // AC01: Restrict access to owning CUSTOMER or assigned STAFF (with MANAGER/ADMIN override)
        validateParticipantAccess(commission, callerId, senderRole, isPrivilegedManagement);

        // AC02: Require commission chat status to be active (PAID through AWAITING_ESTIMATE)
        if (commission.getStatus() == null || !commission.getStatus().isChatActive()) {
            log.warn("Attempted to send chat message while chat is inactive [commissionId={}] [status={}] [callerId={}] [senderRole={}]",
                    commissionId, commission.getStatus(), callerId, senderRole);
            throw new InvalidCommissionStatusTransitionException(
                    "Commission chat is not active in current status: " + commission.getStatus()
            );
        }

        // Auto-assign staff if an unassigned ticket is replied to by a staff member
        if (senderRole == CommissionMessageSenderRole.STAFF && commission.getAssignedStaffId() == null) {
            commission.setAssignedStaffId(callerId);
            commissionRequestRepository.save(commission);
        }

        ValidatedPayload payload = validateAndNormalizePayload(commissionId, request);

        CommissionMessageEntity entity = CommissionMessageEntity.create(
                commissionId,
                callerId,
                senderRole,
                payload.messageType(),
                payload.content(),
                payload.mediaUrl(),
                null
        );

        CommissionMessageEntity saved = commissionMessageRepository.save(entity);
        CommissionMessageResponse response = commissionMessageMapper.toResponse(saved);

        chatBroadcastPort.broadcastMessage(commissionId, response);

        log.info("Commission chat message sent [commissionId={}] [messageId={}] [senderRole={}] [senderId={}] [messageType={}]",
                commissionId, saved.getMessageId(), senderRole, callerId, payload.messageType());

        return response;
    }

    private void validateParticipantAccess(
            CommissionRequestEntity commission,
            Long callerId,
            CommissionMessageSenderRole senderRole,
            boolean isPrivilegedManagement) {

        if (senderRole == CommissionMessageSenderRole.CUSTOMER) {
            if (!commission.isOwnedBy(callerId)) {
                log.warn("Commission chat ownership mismatch [commissionId={}] [callerId={}] [ownerCustomerId={}]",
                        commission.getCommissionId(), callerId, commission.getCustomerId());
                throw new CommissionOwnershipMismatchException("You do not have permission to access this commission chat.");
            }
            return;
        }

        if (commission.getAssignedStaffId() != null
                && !commission.getAssignedStaffId().equals(callerId)
                && !isPrivilegedManagement) {
            log.warn("Commission chat staff lock violation [commissionId={}] [assignedStaffId={}] [callerStaffId={}]",
                    commission.getCommissionId(), commission.getAssignedStaffId(), callerId);
            throw new CommissionStaffLockException(
                    "Commission request " + commission.getCommissionId() + " is locked by assigned staff " + commission.getAssignedStaffId()
            );
        }
    }

    private ValidatedPayload validateAndNormalizePayload(Long commissionId, SendCommissionMessageRequest request) {
        if (request == null) {
            throw new InvalidRuleConfigurationException("Message request payload is required.");
        }

        CommissionMessageType type = request.messageType();
        if (type != CommissionMessageType.TEXT && type != CommissionMessageType.IMAGE) {
            throw new InvalidRuleConfigurationException("Only TEXT and IMAGE message types can be sent via chat.");
        }

        if (type == CommissionMessageType.TEXT) {
            if (request.content() == null || request.content().isBlank()) {
                throw new InvalidRuleConfigurationException("Text message content must not be blank.");
            }
            if (request.mediaUrl() != null) {
                throw new InvalidRuleConfigurationException("TEXT messages must not contain a mediaUrl; use IMAGE messageType instead.");
            }
            return new ValidatedPayload(CommissionMessageType.TEXT, request.content(), null);
        }

        // IMAGE message validation: must be a COMMISSION_CHAT upload for this commission; the object key is persisted
        if (request.mediaUrl() == null || request.mediaUrl().isBlank()) {
            throw new InvalidRuleConfigurationException("IMAGE messages require a valid mediaUrl.");
        }
        String objectKey = privateMediaReferenceValidator.requireOwnedObjectKey(
                request.mediaUrl(), MediaPurpose.COMMISSION_CHAT, commissionId);
        String resolvedCaption = request.content() != null ? request.content() : "";
        return new ValidatedPayload(CommissionMessageType.IMAGE, resolvedCaption, objectKey);
    }

    private record ValidatedPayload(
            CommissionMessageType messageType,
            String content,
            String mediaUrl
    ) {}
}