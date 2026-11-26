package com.lumintra.backend.commission.application;

import com.lumintra.backend.commission.api.response.ManagementCommissionDetailResponse;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionAttachmentEntity;
import com.lumintra.backend.commission.domain.model.CommissionRevisionRequestStatus;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRevisionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionSubmissionEntity;
import com.lumintra.backend.commission.infrastructure.persistence.mapper.CommissionRequestMapper;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionAttachmentRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRevisionRequestRepository;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionSubmissionRepository;
import com.lumintra.backend.iam.domain.model.CustomerProfileSnapshot;
import com.lumintra.backend.iam.domain.model.EmployeeProfileSnapshot;
import com.lumintra.backend.iam.domain.service.CustomerLookupService;
import com.lumintra.backend.iam.domain.service.EmployeeLookupService;
import com.lumintra.backend.shared.exception.commission.CommissionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetManagementCommissionDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetManagementCommissionDetailUseCase.class);

    private final CommissionRequestRepository commissionRequestRepository;
    private final CommissionAttachmentRepository commissionAttachmentRepository;
    private final CommissionSubmissionRepository commissionSubmissionRepository;
    private final CommissionRevisionRequestRepository commissionRevisionRequestRepository;
    private final CommissionRequestMapper commissionRequestMapper;
    private final CustomerLookupService customerLookupService;
    private final EmployeeLookupService employeeLookupService;

    public ManagementCommissionDetailResponse execute(Long commissionId, Long callerId, boolean isPrivileged) {
        if (commissionId == null || commissionId <= 0) {
            throw new CommissionNotFoundException("Commission request not found.");
        }

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId)
                .orElseThrow(() -> {
                    log.warn("Commission request not found [commissionId={}] [callerId={}]", commissionId, callerId);
                    return new CommissionNotFoundException("Commission request not found with id: " + commissionId);
                });

        if (!isPrivileged) {
            if (commission.getAssignedDesignerId() == null || !commission.getAssignedDesignerId().equals(callerId)) {
                log.warn("Access denied: Caller is not assigned designer [commissionId={}] [callerId={}] [assignedDesignerId={}]",
                        commissionId, callerId, commission.getAssignedDesignerId());
                throw new AccessDeniedException("You are not the designer assigned to this commission ticket.");
            }
        }

        List<CommissionAttachmentEntity> attachments = commissionAttachmentRepository
                .findAllByCommissionIdOrderByDisplayOrderAscAttachmentIdAsc(commissionId);

        List<CommissionSubmissionEntity> submissions = commissionSubmissionRepository
                .findAllByCommissionIdOrderByVersionNumberAscSubmissionIdAsc(commissionId);

        List<CommissionRevisionRequestEntity> revisionRequests = commissionRevisionRequestRepository
                .findAllByCommissionIdAndStatusNotOrderByRevisionRequestIdAsc(
                        commissionId, CommissionRevisionRequestStatus.SUPERSEDED);

        CustomerProfileSnapshot customerProfile = commission.getCustomerId() != null
                ? customerLookupService.findProfileByCustomerId(commission.getCustomerId()).orElse(null)
                : null;

        Set<Long> employeeIds = new HashSet<>();
        if (commission.getAssignedStaffId() != null) {
            employeeIds.add(commission.getAssignedStaffId());
        }
        if (commission.getAssignedDesignerId() != null) {
            employeeIds.add(commission.getAssignedDesignerId());
        }
        for (CommissionSubmissionEntity sub : submissions) {
            if (sub.getDesignerId() != null) {
                employeeIds.add(sub.getDesignerId());
            }
            if (sub.getReviewerStaffId() != null) {
                employeeIds.add(sub.getReviewerStaffId());
            }
        }

        Map<Long, EmployeeProfileSnapshot> employeeProfiles = employeeIds.isEmpty()
                ? Collections.emptyMap()
                : employeeLookupService.findProfilesByEmployeeIds(employeeIds);

        log.info("Retrieved management commission detail [commissionId={}] [callerId={}] [isPrivileged={}] [status={}] [attachments={}] [submissions={}]",
                commissionId, callerId, isPrivileged, commission.getStatus(), attachments.size(), submissions.size());

        return commissionRequestMapper.toManagementDetailResponse(
                commission,
                attachments,
                submissions,
                revisionRequests,
                customerProfile,
                employeeProfiles
        );
    }
}
