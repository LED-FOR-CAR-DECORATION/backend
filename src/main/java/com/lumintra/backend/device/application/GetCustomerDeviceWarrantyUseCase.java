package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.response.CustomerDeviceWarrantyResponse;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GetCustomerDeviceWarrantyUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetCustomerDeviceWarrantyUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final WarrantyTicketRepository warrantyTicketRepository;
    private final MediaViewUrlResolver mediaViewUrlResolver;

    @Transactional(readOnly = true)
    public CustomerDeviceWarrantyResponse execute(Long customerId, Long deviceId) {
        if (customerId == null) {
            throw new InsufficientAuthenticationException("Authenticated customer identity is required.");
        }
        if (deviceId == null) {
            throw new LedInstanceNotFoundException("LED hardware device not found.");
        }

        // 1. Fetch LED instance
        LedInstanceEntity instance = ledInstanceRepository.findById(deviceId)
                .orElseThrow(() -> {
                    log.warn("Device not found for warranty view [customerId={}] [deviceId={}]", customerId, deviceId);
                    return new LedInstanceNotFoundException("LED hardware device not found.");
                });

        // 2. Verify ownership
        if (instance.getOwnerCustomerId() == null || !Objects.equals(instance.getOwnerCustomerId(), customerId)) {
            log.warn("Device ownership mismatch for warranty view [customerId={}] [deviceId={}] [ownerCustomerId={}]",
                    customerId, deviceId, instance.getOwnerCustomerId());
            throw new DeviceOwnershipMismatchException("You do not own this LED hardware device.");
        }

        // 3. Resolve warranty status and remaining days
        Instant now = Instant.now();
        WarrantyStatus warrantyStatus = instance.resolveWarrantyStatus(now);
        long remainingDays = 0L;
        if (warrantyStatus == WarrantyStatus.ACTIVE && instance.getWarrantyExpiresAt() != null) {
            long seconds = Math.max(0, Duration.between(now, instance.getWarrantyExpiresAt()).getSeconds());
            remainingDays = (seconds + 86400 - 1) / 86400;
        }

        // 4. Fetch warranty ticket history sorted by created_at DESC
        List<WarrantyTicketEntity> ticketEntities = warrantyTicketRepository
                .findByDeviceSerialNumberOrderByCreatedAtDesc(instance.getSerialNumber());

        List<CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse> ticketHistory = ticketEntities.stream()
                .map(this::mapToSummary)
                .toList();

        // 5. Identify active ticket (PENDING or APPROVED)
        CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse activeTicket = ticketEntities.stream()
                .filter(WarrantyTicketEntity::isActive)
                .findFirst()
                .map(this::mapToSummary)
                .orElse(null);

        log.info("Retrieved device warranty coverage [customerId={}] [deviceId={}] [serialNumber={}] [status={}] [ticketsCount={}]",
                customerId, deviceId, instance.getSerialNumber(), warrantyStatus, ticketHistory.size());

        return new CustomerDeviceWarrantyResponse(
                instance.getInstanceId(),
                instance.getSerialNumber(),
                instance.getDeviceName(),
                instance.getBoundAt(),
                instance.getWarrantyExpiresAt(),
                warrantyStatus,
                remainingDays,
                activeTicket,
                ticketHistory
        );
    }

    private CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse mapToSummary(WarrantyTicketEntity ticket) {
        return new CustomerDeviceWarrantyResponse.WarrantyTicketSummaryResponse(
                ticket.getTicketId(),
                ticket.getStatus(),
                ticket.getIssueDescription(),
                mediaViewUrlResolver.toViewUrls(ticket.getEvidenceMediaUrls()),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
