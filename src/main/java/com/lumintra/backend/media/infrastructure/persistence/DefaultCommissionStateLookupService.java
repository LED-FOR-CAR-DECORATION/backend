package com.lumintra.backend.media.infrastructure.persistence;

import com.lumintra.backend.media.domain.service.CommissionStateLookupService;

import java.util.Set;

/**
 * Fallback lean implementation of {@link CommissionStateLookupService} until the Commission feature tables are added.
 * Registered conditionally via {@link com.lumintra.backend.media.infrastructure.config.MediaCommissionFallbackConfig}.
 */
public class DefaultCommissionStateLookupService implements CommissionStateLookupService {

    @Override
    public boolean isChatActiveAndParticipant(Long commissionId, Long callerAccountId, Set<String> callerRoles) {
        if (commissionId == null || commissionId <= 0 || callerAccountId == null || callerAccountId <= 0
                || callerRoles == null || callerRoles.isEmpty()) {
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
