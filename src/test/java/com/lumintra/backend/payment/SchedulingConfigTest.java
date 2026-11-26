package com.lumintra.backend.payment;

import com.lumintra.backend.shared.config.SchedulingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulingConfigTest {

    @Test
    @DisplayName("SCHED-01 [N]: SchedulingConfig is annotated with @Configuration and @EnableScheduling")
    void schedulingConfig_hasConfigurationAndEnableScheduling() {
        Configuration configAnn = SchedulingConfig.class.getAnnotation(Configuration.class);
        EnableScheduling enableAnn = SchedulingConfig.class.getAnnotation(EnableScheduling.class);

        assertThat(configAnn).isNotNull();
        assertThat(enableAnn).isNotNull();
    }

    @Test
    @DisplayName("SCHED-02 [N]: SchedulingConfig specifies ConditionalOnProperty app.scheduling.enabled")
    void schedulingConfig_hasConditionalOnProperty() {
        ConditionalOnProperty condAnn = SchedulingConfig.class.getAnnotation(ConditionalOnProperty.class);

        assertThat(condAnn).isNotNull();
        assertThat(condAnn.name()).containsExactly("app.scheduling.enabled");
        assertThat(condAnn.havingValue()).isEqualTo("true");
        assertThat(condAnn.matchIfMissing()).isTrue();
    }

    @Test
    @DisplayName("SCHED-03 [B]: SchedulingConfig can be instantiated as a Spring configuration component")
    void schedulingConfig_canBeInstantiated() {
        SchedulingConfig config = new SchedulingConfig();
        assertThat(config).isNotNull();
    }
}
