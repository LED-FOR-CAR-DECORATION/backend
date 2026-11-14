package com.lumintra.backend.device.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for hardware QR payload signing and verification.
 */
@ConfigurationProperties(prefix = "app.device")
public record DeviceQrProperties(
        String factoryQrSecret
) {
    @ConstructorBinding
    public DeviceQrProperties {
    }
}
