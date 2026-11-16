package com.lumintra.backend.media.infrastructure.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.util.Base64;

@Configuration
public class FirebaseStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseStorageConfig.class);

    @Value("${lumintra.storage.firebase.credentials-base64:}")
    private String credentialsBase64;

    @Bean
    @ConditionalOnMissingBean(Storage.class)
    public Storage googleCloudStorage() {
        if (credentialsBase64 != null && !credentialsBase64.isBlank()) {
            try {
                byte[] decoded = Base64.getDecoder().decode(credentialsBase64.trim());
                try (ByteArrayInputStream is = new ByteArrayInputStream(decoded)) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(is);
                    log.info("Initialized Google Cloud Storage using decoded base64 service account credentials");
                    return StorageOptions.newBuilder()
                            .setCredentials(credentials)
                            .build()
                            .getService();
                }
            } catch (Exception ex) {
                log.warn("Failed to initialize Google Cloud Storage from base64 credentials, falling back: {}", ex.getMessage());
            }
        }
        try {
            return StorageOptions.getDefaultInstance().getService();
        } catch (Exception ex) {
            log.warn("Default Google credentials not available, initializing default unauthenticated Storage instance: {}", ex.getMessage());
            return StorageOptions.newBuilder().setProjectId("lumintra-fallback").build().getService();
        }
    }
}
