package com.lumintra.backend.device.application;

import com.lumintra.backend.device.api.request.WarrantyClaimFilterRequest;
import com.lumintra.backend.device.api.response.ManagedWarrantyClaimResponse;
import com.lumintra.backend.device.infrastructure.persistence.entity.LedInstanceEntity;
import com.lumintra.backend.device.infrastructure.persistence.entity.WarrantyTicketEntity;
import com.lumintra.backend.device.infrastructure.persistence.repository.LedInstanceRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketRepository;
import com.lumintra.backend.device.infrastructure.persistence.repository.WarrantyTicketSpecifications;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListManagedWarrantyClaimsUseCase {

    private final WarrantyTicketRepository warrantyTicketRepository;
    private final LedInstanceRepository ledInstanceRepository;
    private final CustomerLookupService customerLookupService;

    @Transactional(readOnly = true)
    public Page<ManagedWarrantyClaimResponse> execute(WarrantyClaimFilterRequest filter) {
        WarrantyClaimFilterRequest safeFilter = filter != null ? filter : new WarrantyClaimFilterRequest();

        Set<Long> matchedCustomerIds = Collections.emptySet();
        if (safeFilter.query() != null && !safeFilter.query().isBlank()) {
            matchedCustomerIds = customerLookupService.findCustomerIdsByKeyword(safeFilter.query());
        }

        Specification<WarrantyTicketEntity> spec = WarrantyTicketSpecifications.withFilter(
                safeFilter.status(),
                safeFilter.query(),
                matchedCustomerIds
        );

        Pageable pageable = PageRequest.of(
                safeFilter.page(),
                safeFilter.size(),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<WarrantyTicketEntity> ticketPage = warrantyTicketRepository.findAll(spec, pageable);
        if (ticketPage.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, ticketPage.getTotalElements());
        }

        Set<Long> customerIds = ticketPage.getContent().stream()
                .map(WarrantyTicketEntity::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, CustomerProfileSnapshot> customerMap = customerLookupService.findProfilesByCustomerIds(customerIds);

        Set<String> serialNumbers = ticketPage.getContent().stream()
                .map(WarrantyTicketEntity::getDeviceSerialNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, LedInstanceEntity> deviceMap = ledInstanceRepository.findBySerialNumberIn(serialNumbers).stream()
                .collect(Collectors.toMap(
                        LedInstanceEntity::getSerialNumber,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        List<ManagedWarrantyClaimResponse> items = ticketPage.getContent().stream()
                .map(ticket -> toResponse(ticket, customerMap.get(ticket.getCustomerId()), deviceMap.get(ticket.getDeviceSerialNumber())))
                .toList();

        return new PageImpl<>(items, pageable, ticketPage.getTotalElements());
    }

    private ManagedWarrantyClaimResponse toResponse(
            WarrantyTicketEntity ticket,
            CustomerProfileSnapshot customer,
            LedInstanceEntity device) {
        return new ManagedWarrantyClaimResponse(
                ticket.getTicketId(),
                ticket.getCustomerId(),
                customer != null ? customer.fullName() : null,
                customer != null ? customer.email() : null,
                customer != null ? customer.phone() : null,
                ticket.getDeviceSerialNumber(),
                device != null ? device.getDeviceName() : null,
                ticket.getOrderItemId(),
                ticket.getStatus(),
                ticket.getIssueDescription(),
                ticket.getEvidenceMediaUrls(),
                ticket.getResolutionNotes(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt()
        );
    }
}
