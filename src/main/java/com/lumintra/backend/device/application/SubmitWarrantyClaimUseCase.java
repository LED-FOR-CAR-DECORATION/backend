package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.SubmitWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.domain.model.InstanceFulfillmentSnapshot;
import com.lumintra.backend.device.domain.model.WarrantyStatus;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.domain.service.OrderFulfillmentLookupService;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.media.domain.service.MediaUrlValidator;
import com.lumintra.backend.shared.exception.device.DeviceOwnershipMismatchException;
import com.lumintra.backend.shared.exception.device.LedInstanceNotFoundException;
import com.lumintra.backend.shared.exception.device.WarrantyClaimConflictException;
import com.lumintra.backend.shared.exception.device.WarrantyExpiredException;
import com.lumintra.backend.shared.exception.device.WarrantyNotActiveException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SubmitWarrantyClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(SubmitWarrantyClaimUseCase.class);

    private final LedInstanceRepository ledInstanceRepository;
    private final WarrantyTicketRepository warrantyTicketRepository;
    private final OrderFulfillmentLookupService orderFulfillmentLookupService;
    private final MediaUrlValidator mediaUrlValidator;

    @Transactional
    public WarrantyClaimResponse execute(Long customerId, Long deviceId, SubmitWarrantyClaimRequest request) {
        if (customerId == null) {
            throw new InsufficientAuthenticationException("Authenticated customer identity is required.");
        }
        if (deviceId == null) {
            throw new LedInstanceNotFoundException("LED hardware device not found.");
        }

        // 1. Validate evidence media URLs if provided (MED-01)
        List<String> validatedEvidenceUrls = new ArrayList<>();
        if (request != null && request.evidenceMediaUrls() != null) {
            for (String url : request.evidenceMediaUrls()) {
                if (url != null && !url.isBlank()) {
                    String trimmedUrl = url.strip();
                    mediaUrlValidator.validateMediaUrl(trimmedUrl);
                    validatedEvidenceUrls.add(trimmedUrl);
                }
            }
        }

        // 2. Fetch LED instance
        LedInstanceEntity instance = ledInstanceRepository.findById(deviceId)
                .orElseThrow(() -> {
                    log.warn("Device not found for warranty claim [customerId={}] [deviceId={}]", customerId, deviceId);
                    return new LedInstanceNotFoundException("LED hardware device not found.");
                });

        // 3. Verify ownership
        if (instance.getOwnerCustomerId() == null || !Objects.equals(instance.getOwnerCustomerId(), customerId)) {
            log.warn("Device ownership mismatch for warranty claim [customerId={}] [deviceId={}] [ownerCustomerId={}]",
                    customerId, deviceId, instance.getOwnerCustomerId());
            throw new DeviceOwnershipMismatchException("You do not own this LED hardware device.");
        }

        // 4. Verify warranty coverage (BR-FL2-02, BR-FL5-02)
        WarrantyStatus warrantyStatus = instance.resolveWarrantyStatus(Instant.now());
        if (warrantyStatus == WarrantyStatus.NOT_ACTIVATED) {
            log.warn("Warranty not activated for device [customerId={}] [deviceId={}] [serialNumber={}]",
                    customerId, deviceId, instance.getSerialNumber());
            throw new WarrantyNotActiveException("Warranty for this device has not been activated.");
        }
        if (warrantyStatus == WarrantyStatus.EXPIRED) {
            log.warn("Warranty expired for device [customerId={}] [deviceId={}] [serialNumber={}] [expiresAt={}]",
                    customerId, deviceId, instance.getSerialNumber(), instance.getWarrantyExpiresAt());
            throw new WarrantyExpiredException("Warranty for this device has expired.");
        }

        // 5. Prevent duplicate active claims (PENDING or APPROVED)
        boolean hasActiveClaim = warrantyTicketRepository.existsByDeviceSerialNumberAndStatusIn(
                instance.getSerialNumber(),
                List.of(WarrantyTicketStatus.PENDING, WarrantyTicketStatus.APPROVED)
        );
        if (hasActiveClaim) {
            log.warn("Active warranty claim already exists for device [customerId={}] [deviceId={}] [serialNumber={}]",
                    customerId, deviceId, instance.getSerialNumber());
            throw new WarrantyClaimConflictException("An active warranty claim already exists for this device.");
        }

        // 6. Resolve linked order item ID
        Long orderItemId = orderFulfillmentLookupService.findFulfillmentByInstanceId(instance.getInstanceId())
                .map(InstanceFulfillmentSnapshot::orderItemId)
                .orElse(null);

        // 7. Persist warranty ticket
        WarrantyTicketEntity ticket = WarrantyTicketEntity.createPending(
                customerId,
                orderItemId,
                instance.getSerialNumber(),
                request != null ? request.issueDescription() : null,
                validatedEvidenceUrls
        );
        ticket = warrantyTicketRepository.save(ticket);

        log.info("Warranty claim submitted successfully [ticketId={}] [customerId={}] [deviceId={}] [serialNumber={}] [evidenceCount={}]",
                ticket.getTicketId(), customerId, deviceId, instance.getSerialNumber(), validatedEvidenceUrls.size());

        return new WarrantyClaimResponse(
                ticket.getTicketId(),
                instance.getInstanceId(),
                instance.getSerialNumber(),
                instance.getDeviceName(),
                ticket.getStatus(),
                ticket.getIssueDescription(),
                ticket.getEvidenceMediaUrls(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt()
        );
    }
}
