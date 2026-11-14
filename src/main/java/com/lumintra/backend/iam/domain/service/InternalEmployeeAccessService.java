package com.lumintra.backend.iam.domain.service;

import java.util.Set;

/**
 * Read-only domain boundary exposed by IAM to verify against the database that an authenticated
 * caller is an active EMPLOYEE holding one of the required internal roles (anti-stale-JWT).
 */
public interface InternalEmployeeAccessService {

    Long requireActiveEmployeeAccountId(String subject, Set<String> allowedRoles);
}
