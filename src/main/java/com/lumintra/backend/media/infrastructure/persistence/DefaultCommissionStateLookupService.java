package com.lumintra.backend.media.infrastructure.persistence;

import com.lumintra.backend.media.domain.service.CommissionStateLookupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Fallback lean implementation of CommissionStateLookupService until the Commission feature tables are added.
 */
@Service
@ConditionalOnMissingBean(type = "com.lumintra.backend.commission.domain.service.CommissionStateLookupService")
public class DefaultCommissionStateLookupService implements CommissionStateLookupService {

    @Override
    public boolean isChatActiveAndParticipant(Long commissionId, Long callerAccountId, Set<String> callerRoles) {
        if (commissionId == null || commissionId <= 0 || callerAccountId == null) {
            return false;
        }
        // In default stub before commission feature tables exist, accept valid non-null positive identifiers
        return true;
    }

    @Override
    public boolean isAssignedDesigner(Long commissionId, Long designerEmployeeId) {
        if (commissionId == null || commissionId <= 0 || designerEmployeeId == null || designerEmployeeId <= 0) {
            return false;
        }
        // In default stub before commission feature tables exist, accept valid non-null positive identifiers
        return true;
    }
}
