package com.lumintra.backend.commission.infrastructure.adapter;

import com.lumintra.backend.commission.infrastructure.persistence.entity.CommissionRequestEntity;
import com.lumintra.backend.commission.infrastructure.persistence.repository.CommissionRequestRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.media.domain.service.CommissionStateLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Infrastructure adapter implementing Media Service's CommissionStateLookupService
 * to enforce real commission state and ownership checks for COMMISSION_CHAT and COMMISSION_DRAFT uploads.
 */
@Component
@RequiredArgsConstructor
public class CommissionStateLookupAdapter implements CommissionStateLookupService {

    private static final Set<String> MANAGEMENT_ROLES = Set.of("STAFF", "MANAGER", "ADMIN");

    private final CommissionRequestRepository commissionRequestRepository;
    private final InternalEmployeeAccessService internalEmployeeAccessService;

    @Override
    @Transactional(readOnly = true)
    public boolean isChatActiveAndParticipant(Long commissionId, Long callerAccountId, Set<String> callerRoles) {
        if (commissionId == null || commissionId <= 0 || callerAccountId == null || callerAccountId <= 0
                || callerRoles == null || callerRoles.isEmpty()) {
            return false;
        }

        CommissionRequestEntity commission = commissionRequestRepository.findById(commissionId).orElse(null);
        if (commission == null || commission.getStatus() == null || !commission.getStatus().isChatActive()) {
            return false;
        }

        if (callerRoles.contains("CUSTOMER")) {
            return commission.isOwnedBy(callerAccountId);
        }

        if (callerRoles.contains("MANAGER") || callerRoles.contains("ADMIN")) {
            return true;
        }

        if (callerRoles.contains("STAFF")) {
            try {
                Long staffEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(
                        String.valueOf(callerAccountId), MANAGEMENT_ROLES
                );
                return commission.getAssignedStaffId() == null
                        || commission.getAssignedStaffId().equals(staffEmployeeId);
            } catch (RuntimeException ex) {
                return false;
            }
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssignedDesigner(Long commissionId, Long designerEmployeeId) {
        if (commissionId == null || commissionId <= 0 || designerEmployeeId == null || designerEmployeeId <= 0) {
            return false;
        }
        return commissionRequestRepository.findById(commissionId)
                .map(c -> designerEmployeeId.equals(c.getAssignedDesignerId()))
                .orElse(false);
    }
}