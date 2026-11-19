package com.lumintra.backend.commission.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumintra.backend.commission.api.request.UpdateCommissionPresetsRequest;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.application.UpdateCommissionPresetsUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.InvalidRuleConfigurationException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommissionPresetManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class CommissionPresetManagementControllerTest {

    private static final String PATH = "/api/management/commissions/presets";

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UpdateCommissionPresetsUseCase useCase;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;
    @MockitoBean
    private Tracer tracer;

    private static UpdateCommissionPresetsRequest.Preset.PresetBuilder basic() {
        return UpdateCommissionPresetsRequest.Preset.builder()
                .tierCode("BASIC").tierName("Basic").basePrice(new BigDecimal("150000"))
                .maxRevisions(1).includedOptionIds(List.of(1L));
    }

    private String body(UpdateCommissionPresetsRequest.Preset... presets) throws Exception {
        return objectMapper.writeValueAsString(new UpdateCommissionPresetsRequest(List.of(presets)));
    }

    @Test
    void managerUpdatesPresets() throws Exception {
        when(useCase.execute(any())).thenReturn(List.of(
                ManagementPricingTierResponse.builder().tierCode("BASIC").maxRevisions(1).build()));

        UpdateCommissionPresetsRequest.Preset preset = basic().build();
        mockMvc.perform(put(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(preset)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].tierCode").value("BASIC"));
        verify(useCase).execute(new UpdateCommissionPresetsRequest(List.of(preset)));
    }

    @Test
    void adminIsAllowed() throws Exception {
        when(useCase.execute(any())).thenReturn(List.of());
        mockMvc.perform(put(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(basic().build())))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousCallerGets401() throws Exception {
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(basic().build())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(useCase);
    }

    @Test
    void staffCallerGets403() throws Exception {
        mockMvc.perform(put(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("staff").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(basic().build())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void invalidFieldsAreRejectedWith400() throws Exception {
        String[] invalidBodies = {
                "{\"presets\":[]}",
                body(basic().discountPercent(new BigDecimal("100.01")).build()),
                body(basic().basePrice(new BigDecimal("-1")).build()),
                body(basic().maxRevisions(6).build()),
                body(basic().maxRevisions(0).build()),
                body(basic().extraRevisionFeePercent(new BigDecimal("101")).build()),
                body(basic().tierCode("1BAD").build()),
                body(basic().tierName(" ").build()),
                body(basic().includedOptionIds(List.of(0L)).build())
        };
        for (String invalid : invalidBodies) {
            mockMvc.perform(put(PATH)
                            .with(SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalid))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(useCase);
    }

    @Test
    void boundaryValuesAreAccepted() throws Exception {
        when(useCase.execute(any())).thenReturn(List.of());
        mockMvc.perform(put(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(basic().basePrice(BigDecimal.ZERO).discountPercent(new BigDecimal("100"))
                                .extraRevisionFeePercent(BigDecimal.ZERO).build())))
                .andExpect(status().isOk());
    }

    @Test
    void configurationErrorMapsTo400() throws Exception {
        when(useCase.execute(any())).thenThrow(new InvalidRuleConfigurationException("CUSTOM tier cannot be deactivated."));
        mockMvc.perform(put(PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("manager").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(basic().build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RULE_CONFIGURATION"));
    }
}
