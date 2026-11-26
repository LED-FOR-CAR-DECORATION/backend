package com.lumintra.backend.monitoring.domain.service;

import com.lumintra.backend.monitoring.domain.model.SystemComponentsStatus;

/**
 * Reads the current health of the database, Redis and the JVM.
 * Implementations must not throw: an unreachable component is reported as DOWN.
 */
public interface SystemComponentsProbe {

    SystemComponentsStatus probe();
}
