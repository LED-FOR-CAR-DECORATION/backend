package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.ProcessWarrantyClaimRequest;
import com.lumintra.backend.device.api.response.WarrantyClaimResponse;
import com.lumintra.backend.device.domain.model.WarrantyTicketStatus;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.shared.exception.device.WarrantyClaimNotFoundException;
import com.lumintra.backend.media.domain.service.MediaViewUrlResolver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProcessWarrantyClaimUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProcessWarrantyClaimUseCase.class);

    private final WarrantyTicketRepository warrantyTicketRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final MediaViewUrlResolver mediaViewUrlResolver;

    @Transactional
    public WarrantyClaimResponse execute(Long ticketId, ProcessWarrantyClaimRequest request) {
        if (ticketId == null || ticketId <= 0) {
            throw new WarrantyClaimNotFoundException("Warranty claim not found.");
        }

        WarrantyTicketEntity ticket = warrantyTicketRepository.findById(ticketId)
                .orElseThrow(() -> new WarrantyClaimNotFoundException("Warranty claim with ID " + ticketId + " not found."));

        WarrantyTicketStatus previousStatus = ticket.getStatus();
        ticket.process(request.status(), request.resolutionNotes());
        WarrantyTicketEntity savedTicket = warrantyTicketRepository.save(ticket);

        LedInstanceEntity device = ledInstanceRepository.findBySerialNumber(savedTicket.getDeviceSerialNumber())
                .orElse(null);

        log.info("Warranty claim status processed [ticketId={}] [previousStatus={}] [newStatus={}]",
                ticketId, previousStatus, savedTicket.getStatus());

        return new WarrantyClaimResponse(
                savedTicket.getTicketId(),
                device != null ? device.getInstanceId() : null,
                savedTicket.getDeviceSerialNumber(),
                device != null ? device.getDeviceName() : null,
                savedTicket.getStatus(),
                savedTicket.getIssueDescription(),
                mediaViewUrlResolver.toViewUrls(savedTicket.getEvidenceMediaUrls()),
                savedTicket.getResolutionNotes(),
                savedTicket.getCreatedAt(),
                savedTicket.getUpdatedAt()
        );
    }
}
