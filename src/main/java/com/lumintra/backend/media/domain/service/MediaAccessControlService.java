package com.lumintra.backend.media.domain.service;

import com.lumintra.backend.device.domain.service.DeviceOwnershipService;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.media.domain.model.MediaPurpose;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Enforces role-based and ownership-based access control matrix according to MED-01 AC01.
 */
@Service
@RequiredArgsConstructor
public class MediaAccessControlService {

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final DeviceOwnershipService deviceOwnershipService;
    private final CommissionStateLookupService commissionStateLookupService;

    public void verifyAccess(String subject, Set<String> roles, MediaPurpose purpose, Long targetId) {
        if (purpose == null) {
            throw new AccessDeniedException("Upload purpose is required.");
        }
        if (subject == null || subject.isBlank() || roles == null || roles.isEmpty()) {
            throw new AccessDeniedException("Authenticated user identity and roles are required.");
        }

        Long callerAccountId = parseAccountId(subject);

        switch (purpose) {
            case COMMISSION_REFERENCE -> {
                if (!roles.contains("CUSTOMER") && !hasAnyRole(roles, "MANAGER", "ADMIN")) {
                    throw new AccessDeniedException("Only customers are authorized to upload commission reference images.");
                }
            }
            case COMMISSION_CHAT -> {
                if (targetId == null || targetId <= 0) {
                    throw new AccessDeniedException("targetId (commissionId) is required for COMMISSION_CHAT uploads.");
                }
                boolean allowedRole = hasAnyRole(roles, "CUSTOMER", "STAFF", "MANAGER", "ADMIN");
                if (!allowedRole) {
                    throw new AccessDeniedException("Caller role is not authorized to upload commission chat attachments.");
                }
                boolean activeAndParticipant = commissionStateLookupService.isChatActiveAndParticipant(targetId, callerAccountId, roles);
                if (!activeAndParticipant) {
                    throw new AccessDeniedException("Commission chat is not active or caller is not an authorized participant.");
                }
            }
            case COMMISSION_DRAFT -> {
                if (!roles.contains("DESIGNER")) {
                    throw new AccessDeniedException("Only designers are authorized to upload commission drafts.");
                }
                if (targetId == null || targetId <= 0) {
                    throw new AccessDeniedException("targetId (commissionId) is required for COMMISSION_DRAFT uploads.");
                }
                Long designerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, Set.of("DESIGNER"));
                boolean isAssigned = commissionStateLookupService.isAssignedDesigner(targetId, designerEmployeeId);
                if (!isAssigned) {
                    throw new AccessDeniedException("Designer is not assigned to this commission.");
                }
            }
            case WARRANTY_EVIDENCE -> {
                if (!roles.contains("CUSTOMER")) {
                    throw new AccessDeniedException("Only customers are authorized to upload warranty evidence.");
                }
                if (targetId == null || targetId <= 0) {
                    throw new AccessDeniedException("targetId (deviceId) is required for WARRANTY_EVIDENCE uploads.");
                }
                boolean isOwned = deviceOwnershipService.isDeviceOwnedByCustomer(targetId, callerAccountId);
                if (!isOwned) {
                    throw new AccessDeniedException("Device does not belong to the authenticated customer.");
                }
            }
            case CHARACTER_ASSET -> {
                if (!hasAnyRole(roles, "DESIGNER", "MANAGER", "ADMIN")) {
                    throw new AccessDeniedException("Only designers, managers, and admins are authorized to upload character assets.");
                }
                if (roles.contains("DESIGNER")) {
                    internalEmployeeAccessService.requireActiveEmployeeId(subject, Set.of("DESIGNER"));
                } else {
                    internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, Set.of("MANAGER", "ADMIN"));
                }
            }
            case STORE_PRODUCT -> {
                if (!hasAnyRole(roles, "MANAGER", "ADMIN")) {
                    throw new AccessDeniedException("Only managers and admins are authorized to upload store product media.");
                }
                internalEmployeeAccessService.requireActiveEmployeeAccountId(subject, Set.of("MANAGER", "ADMIN"));
            }
        }
    }

    private boolean hasAnyRole(Set<String> roles, String... candidateRoles) {
        for (String candidate : candidateRoles) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private Long parseAccountId(String subject) {
        try {
            return Long.parseLong(subject.trim());
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Invalid account ID format in token subject.");
        }
    }
}
