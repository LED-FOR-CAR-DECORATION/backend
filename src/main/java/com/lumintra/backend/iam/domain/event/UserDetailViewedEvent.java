package com.lumintra.backend.iam.domain.event;

import java.time.Instant;

public record UserDetailViewedEvent(
        Long viewerAccountId, Long targetAccountId, String viewerRole, Instant viewedAt) {}
