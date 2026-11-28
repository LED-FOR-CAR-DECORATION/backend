package com.lumintra.backend.monitoring.application;

import com.lumintra.backend.monitoring.domain.model.MetricSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the most recent per-minute snapshots in memory for the live metrics view (MON-02).
 * Contents are lost on restart; history comes from the stored buckets instead.
 */
@Component
public class MetricSnapshotBuffer {

    static final int CAPACITY = 60;

    private final Deque<MetricSnapshot> snapshots = new ArrayDeque<>(CAPACITY);

    public synchronized void add(MetricSnapshot snapshot) {
        if (snapshots.size() == CAPACITY) {
            snapshots.removeFirst();
        }
        snapshots.addLast(snapshot);
    }

    /** Snapshots from oldest to newest. */
    public synchronized List<MetricSnapshot> getAll() {
        return List.copyOf(snapshots);
    }
}
