package com.lumintra.backend.media.domain.service;

import java.util.Set;

/**
 * Domain boundary contract used by Media Service to verify commission chat room activity and participant ownership.
 * Implemented with a default safe adapter until the Commission feature entities are fully populated.
 */
public interface CommissionStateLookupService {

    /**
     * Checks whether the commission chat room is active (PAID through PREVIEW_DELIVERED)
     * and the caller is an authorized participant (owning CUSTOMER, assigned STAFF, or MANAGER/ADMIN).
     *
     * @param commissionId unique ID of the commission
     * @param callerAccountId account ID of the caller
     * @param callerRoles set of role names of the caller (e.g. CUSTOMER, STAFF, MANAGER, ADMIN)
     * @return true if chat room is active and caller is authorized; false otherwise
     */
    boolean isChatActiveAndParticipant(Long commissionId, Long callerAccountId, Set<String> callerRoles);

    /**
     * Checks whether the given designer employee ID is the assigned designer for the commission.
     *
     * @param commissionId unique ID of the commission
     * @param designerEmployeeId employee ID of the designer
     * @return true if designer is assigned to the commission; false otherwise
     */
    boolean isAssignedDesigner(Long commissionId, Long designerEmployeeId);
}
