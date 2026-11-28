package com.lumintra.backend.monitoring.infrastructure.metrics;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Identifies this backend instance in stored metric buckets, so several instances can write
 * rows for the same minute without colliding (BR-GEN-25).
 * Uses {@code app.monitoring.instance-id} when set, otherwise {@code <hostname>-<pid>}.
 */
@Component
public class MonitoringInstanceId {

    private static final int MAX_LENGTH = 100;

    private final String value;

    public MonitoringInstanceId(@Value("${app.monitoring.instance-id:}") String configured) {
        String resolved = configured != null && !configured.isBlank()
                ? configured.trim()
                : hostName() + "-" + ProcessHandle.current().pid();
        this.value = resolved.length() > MAX_LENGTH ? resolved.substring(0, MAX_LENGTH) : resolved;
    }

    public String value() {
        return value;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            return "unknown-host";
        }
    }
}
