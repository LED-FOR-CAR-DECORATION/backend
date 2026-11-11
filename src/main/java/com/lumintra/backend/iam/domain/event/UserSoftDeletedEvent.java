package com.lumintra.backend.iam.domain.event;

import com.lumintra.backend.iam.domain.model.UserDeletionReason;

import java.time.Instant;

public record UserSoftDeletedEvent(
        long actorId, long accountId, UserDeletionReason reason, Instant deletedAt) {}
