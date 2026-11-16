package com.lumintra.backend.media;

import com.lumintra.backend.media.domain.service.CommissionStateLookupService;
import com.lumintra.backend.media.infrastructure.config.MediaCommissionFallbackConfig;
import com.lumintra.backend.media.infrastructure.persistence.DefaultCommissionStateLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCommissionStateLookupServiceTest {

    private final DefaultCommissionStateLookupService fallbackService = new DefaultCommissionStateLookupService();

    @Test
    @DisplayName("MED-01 [N]: Fallback config registers DefaultCommissionStateLookupService when no real bean exists")
    void fallbackConfig_whenNoOtherBean_shouldRegisterDefaultStub() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(MediaCommissionFallbackConfig.class);
            context.refresh();

            Map<String, CommissionStateLookupService> beans = context.getBeansOfType(CommissionStateLookupService.class);
            assertThat(beans).hasSize(1);
            assertThat(context.getBean(CommissionStateLookupService.class))
                    .isInstanceOf(DefaultCommissionStateLookupService.class);
        }
    }

    @Test
    @DisplayName("MED-01 [N]: Fallback config backs off when a real CommissionStateLookupService service exists")
    void fallbackConfig_whenRealServiceRegistered_shouldBackOffWithoutConflict() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            // Register config first to ensure ConditionalOnMissingBean does not depend on scan order
            context.register(MediaCommissionFallbackConfig.class, StubCommissionStateLookupService.class);
            context.refresh();

            Map<String, CommissionStateLookupService> beans = context.getBeansOfType(CommissionStateLookupService.class);
            assertThat(beans).hasSize(1);
            CommissionStateLookupService resolved = context.getBean(CommissionStateLookupService.class);
            assertThat(resolved).isInstanceOf(StubCommissionStateLookupService.class);
            assertThat(resolved.isChatActiveAndParticipant(101L, 10L, Set.of("CUSTOMER"))).isFalse();
            assertThat(resolved.isAssignedDesigner(101L, 50L)).isFalse();
        }
    }

    @Test
    @DisplayName("MED-01 [B]: Fallback validation checks reject invalid inputs and allow valid positive identifiers")
    void fallbackService_validatesIdentifiersAndRoles() {
        assertThat(fallbackService.isChatActiveAndParticipant(101L, 10L, Set.of("CUSTOMER"))).isTrue();
        assertThat(fallbackService.isChatActiveAndParticipant(null, 10L, Set.of("CUSTOMER"))).isFalse();
        assertThat(fallbackService.isChatActiveAndParticipant(0L, 10L, Set.of("CUSTOMER"))).isFalse();
        assertThat(fallbackService.isChatActiveAndParticipant(101L, null, Set.of("CUSTOMER"))).isFalse();
        assertThat(fallbackService.isChatActiveAndParticipant(101L, 0L, Set.of("CUSTOMER"))).isFalse();
        assertThat(fallbackService.isChatActiveAndParticipant(101L, 10L, Set.of())).isFalse();

        assertThat(fallbackService.isAssignedDesigner(101L, 50L)).isTrue();
        assertThat(fallbackService.isAssignedDesigner(null, 50L)).isFalse();
        assertThat(fallbackService.isAssignedDesigner(0L, 50L)).isFalse();
        assertThat(fallbackService.isAssignedDesigner(101L, null)).isFalse();
        assertThat(fallbackService.isAssignedDesigner(101L, 0L)).isFalse();
    }

    @Service
    static class StubCommissionStateLookupService implements CommissionStateLookupService {

        @Override
        public boolean isChatActiveAndParticipant(Long commissionId, Long callerAccountId, Set<String> callerRoles) {
            return false;
        }

        @Override
        public boolean isAssignedDesigner(Long commissionId, Long designerEmployeeId) {
            return false;
        }
    }
}
