package com.lumintra.backend.device;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.device.api.controller.FirmwareUpdateController;
import com.lumintra.backend.device.api.response.FirmwareUpdateResponse;
import com.lumintra.backend.device.application.CheckFirmwareUpdateUseCase;
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

@WebMvcTest(FirmwareUpdateController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class FirmwareUpdateControllerTest {
    private static final String URL = "/api/devices/test-unit/ota/check";
    @Autowired MockMvc mvc;
    @MockitoBean CheckFirmwareUpdateUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void usesAuthenticatedCustomerIdAndReturnsUpdateResult() throws Exception {
        when(useCase.execute("42", "test-unit", "1.9.0"))
                .thenReturn(new FirmwareUpdateResponse(true, "1.9.0", "1.10.0"));
        mvc.perform(get(URL).param("currentVersion", "1.9.0").with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updateAvailable").value(true))
                .andExpect(jsonPath("$.data.latestVersion").value("1.10.0"));
    }

    @Test
    void rejectsMissingVersionsAndUnauthorizedCallers() throws Exception {
        mvc.perform(get(URL).with(user("42").roles("CUSTOMER"))).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("currentVersion", "1.0").with(user("42").roles("ADMIN")))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL).param("currentVersion", "1.0")).andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }
}
