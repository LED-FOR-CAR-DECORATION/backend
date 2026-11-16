package com.lumintra.backend.media.infrastructure.config;

import com.lumintra.backend.media.domain.service.CommissionStateLookupService;
import com.lumintra.backend.media.infrastructure.persistence.DefaultCommissionStateLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the fallback {@link CommissionStateLookupService} bean only when no real
 * implementation (such as the Commission module's service) exists in the application context.
 */
@Configuration
public class MediaCommissionFallbackConfig {

    private static final Logger log = LoggerFactory.getLogger(MediaCommissionFallbackConfig.class);

    @Bean
    @ConditionalOnMissingBean(CommissionStateLookupService.class)
    public CommissionStateLookupService defaultCommissionStateLookupService() {
        log.warn("No CommissionStateLookupService bean present; registering DefaultCommissionStateLookupService fallback stub.");
        return new DefaultCommissionStateLookupService();
    }
}
