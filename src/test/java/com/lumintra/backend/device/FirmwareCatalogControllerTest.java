package com.lumintra.backend.device;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.device.api.controller.FirmwareCatalogController;
import com.lumintra.backend.device.application.ListFirmwareReleasesUseCase;
import com.lumintra.backend.device.domain.model.FirmwareReleaseStatus;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;

import io.micrometer.tracing.Tracer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

@WebMvcTest(FirmwareCatalogController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class FirmwareCatalogControllerTest {
    private static final String URL = "/api/management/admin/firmware";
    @Autowired MockMvc mvc;
    @MockitoBean ListFirmwareReleasesUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void bindsFiltersForAdmin() throws Exception {
        when(useCase.execute(10L, FirmwareReleaseStatus.ACTIVE)).thenReturn(List.of());
        mvc.perform(
                        get(URL).param("modelId", "10")
                                .param("status", "ACTIVE")
                                .with(user("42").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        verify(useCase).execute(10L, FirmwareReleaseStatus.ACTIVE);
    }

    @Test
    void rejectsUnauthorizedCallersAndInvalidFilters() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user("42").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get(URL).param("modelId", "0").with(user("42").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("status", "INVALID").with(user("42").roles("ADMIN")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(useCase);
    }
}
