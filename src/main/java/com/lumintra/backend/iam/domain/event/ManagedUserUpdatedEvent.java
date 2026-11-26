package com.lumintra.backend.iam.domain.event;

import java.time.Instant;
import java.util.Set;

public record ManagedUserUpdatedEvent(
        long actorId, long accountId, Set<String> changedFields, Instant updatedAt) {
    public ManagedUserUpdatedEvent {
        changedFields = Set.copyOf(changedFields);
    }
}
