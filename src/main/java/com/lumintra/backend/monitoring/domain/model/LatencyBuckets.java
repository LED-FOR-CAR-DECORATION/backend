package com.lumintra.backend.monitoring.domain.model;

import java.util.Arrays;

/**
 * Fixed latency bucket boundaries used for API performance metrics (BR-GEN-26).
 * <p>
 * Each stored bucket counts requests whose duration falls in {@code (previous boundary, boundary]};
 * the last slot counts everything above the final boundary. Fixed boundaries keep bucket counts
 * addable across minutes, endpoints and instances, which is what makes percentiles over any
 * time range possible.
 */
public final class LatencyBuckets {

    private static final long[] UPPER_BOUNDS_MS = {25, 50, 100, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 10000};

    /** Number of stored slots: one per boundary plus the overflow slot. */
    public static final int SLOT_COUNT = UPPER_BOUNDS_MS.length + 1;

    private LatencyBuckets() {
    }

    public static long[] upperBoundsMs() {
        return Arrays.copyOf(UPPER_BOUNDS_MS, UPPER_BOUNDS_MS.length);
    }

    /** Index of the slot that a request of the given duration falls into. */
    public static int slotFor(long durationNanos) {
        double durationMs = durationNanos / 1_000_000.0;
        for (int i = 0; i < UPPER_BOUNDS_MS.length; i++) {
            if (durationMs <= UPPER_BOUNDS_MS[i]) {
                return i;
            }
        }
        return UPPER_BOUNDS_MS.length;
    }
}
