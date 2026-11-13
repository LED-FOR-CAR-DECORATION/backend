package com.lumintra.backend.device;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.lumintra.backend.device.api.controller.RegisteredDeviceController;
import com.lumintra.backend.device.api.response.RegisteredDeviceResponse;
import com.lumintra.backend.device.application.ListRegisteredDevicesUseCase;
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

@WebMvcTest(RegisteredDeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class RegisteredDeviceControllerTest {
    private static final String URL = "/api/devices";
    @Autowired MockMvc mvc;
    @MockitoBean ListRegisteredDevicesUseCase useCase;
    @MockitoBean JwtTokenProvider jwt;
    @MockitoBean TokenBlacklistService blacklist;
    @MockitoBean Tracer tracer;

    @Test
    void listsOnlyTheAuthenticatedCustomerDevices() throws Exception {
        when(useCase.execute(42L))
                .thenReturn(List.of(new RegisteredDeviceResponse(7L, 3L, "LED-7", "ACTIVE")));
        mvc.perform(get(URL).with(user("42").roles("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].instanceId").value(7));
        verify(useCase).execute(42L);
    }

    @Test
    void deniesGuestsAndStaff() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(URL).with(user("42").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }
}
