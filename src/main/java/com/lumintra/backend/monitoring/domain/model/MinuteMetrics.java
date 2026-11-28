package com.lumintra.backend.monitoring.domain.model;

import java.time.Instant;

/**
 * Stored metrics for one minute, already added up across instances and the selected endpoints.
 * {@code metrics.httpMethod()} and {@code metrics.uriTemplate()} are {@code null} because the row
 * may combine several endpoints.
 */
public record MinuteMetrics(Instant bucketStart, ApiEndpointMetrics metrics) {
}
