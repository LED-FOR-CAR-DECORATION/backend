package com.lumintra.backend.monitoring.domain.model;

/**
 * Current health of the components the backend depends on. Numeric fields are {@code null}
 * when the underlying gauge is not available.
 */
public record SystemComponentsStatus(Database db, Redis redis, Jvm jvm) {

    public static final String STATUS_UP = "UP";
    public static final String STATUS_DOWN = "DOWN";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    public record Database(String status, Integer activeConnections, Integer idleConnections, Integer pendingConnections) {
    }

    public record Redis(String status) {
    }

    /**
     * @param cpuUsage recent CPU usage of the backend process, from 0 to 1
     */
    public record Jvm(Double heapUsedMb, Double heapMaxMb, Integer threadsLive, Double cpuUsage) {
    }
}
