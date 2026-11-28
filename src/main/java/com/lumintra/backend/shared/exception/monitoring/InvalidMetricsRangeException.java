package com.lumintra.backend.shared.exception.monitoring;

/** The requested metrics history range is not allowed (MON-03 AC02). */
public class InvalidMetricsRangeException extends RuntimeException {
    public InvalidMetricsRangeException(String message) {
        super(message);
    }
}
