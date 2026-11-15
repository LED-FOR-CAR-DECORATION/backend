package com.lumintra.backend.commission.api.controller;

import com.lumintra.backend.commission.api.response.BundledOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementCommissionPricingResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingOptionResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingRuleSummaryResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierResponse;
import com.lumintra.backend.commission.api.response.ManagementPricingTierSummaryResponse;
import com.lumintra.backend.commission.application.GetManagementCommissionPricingUseCase;
import com.lumintra.backend.commission.application.GetManagementPricingRuleDetailUseCase;
import com.lumintra.backend.commission.application.GetManagementPricingTierDetailUseCase;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.commission.CommissionRuleNotFoundException;
import com.lumintra.backend.shared.exception.commission.CommissionTierNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommissionManagementPricingController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class CommissionManagementPricingControllerTest {

    private static final String BASE_PATH = "/api/management/commissions/pricing";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetManagementCommissionPricingUseCase getManagementCommissionPricingUseCase;

    @MockitoBean
    private GetManagementPricingTierDetailUseCase getManagementPricingTierDetailUseCase;

    @MockitoBean
    private GetManagementPricingRuleDetailUseCase getManagementPricingRuleDetailUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private ManagementCommissionPricingResponse buildSampleManagementSummaryResponse() {
        ManagementPricingTierSummaryResponse basicTier = ManagementPricingTierSummaryResponse.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .tierName("Gói Cơ Bản (Basic Package)")
                .basePrice(new BigDecimal("79000.00"))
                .customCalculatedPrice(new BigDecimal("100000.00"))
                .savingsAmount(new BigDecimal("21000.00"))
                .maxRevisions(1)
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptionCount(1)
                .build();

        ManagementPricingTierSummaryResponse inactiveTier = ManagementPricingTierSummaryResponse.builder()
                .tierId(3L)
                .tierCode("LEGACY_PRESET")
                .tierName("Gói Cũ (Legacy)")
                .basePrice(new BigDecimal("50000.00"))
                .customCalculatedPrice(new BigDecimal("50000.00"))
                .savingsAmount(BigDecimal.ZERO)
                .maxRevisions(1)
                .isCustom(false)
                .isActive(false)
                .displayOrder(3)
                .includedOptionCount(0)
                .build();

        ManagementPricingRuleSummaryResponse ruleSummary = ManagementPricingRuleSummaryResponse.builder()
                .configId(1L)
                .configCode("FORMAT_TYPE")
                .configName("Định dạng hiển thị (Format Type)")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .dependsOnOptionId(null)
                .optionCount(2)
                .build();

        return ManagementCommissionPricingResponse.builder()
                .tiers(List.of(basicTier, inactiveTier))
                .rules(List.of(ruleSummary))
                .build();
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID01 [N]: Authenticated MANAGER retrieves summary configuration returning 200 OK")
    void getManagementPricing_managerRole_returns200OkWithAllTierAndRuleSummaries() throws Exception {
        ManagementCommissionPricingResponse responseDto = buildSampleManagementSummaryResponse();
        when(getManagementCommissionPricingUseCase.execute()).thenReturn(responseDto);

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tiers[0].tierCode").value("BASIC"))
                .andExpect(jsonPath("$.data.tiers[0].isActive").value(true))
                .andExpect(jsonPath("$.data.tiers[0].includedOptionCount").value(1))
                .andExpect(jsonPath("$.data.tiers[1].tierCode").value("LEGACY_PRESET"))
                .andExpect(jsonPath("$.data.tiers[1].isActive").value(false))
                .andExpect(jsonPath("$.data.rules[0].configCode").value("FORMAT_TYPE"))
                .andExpect(jsonPath("$.data.rules[0].isSystem").value(true))
                .andExpect(jsonPath("$.data.rules[0].optionCount").value(2));

        verify(getManagementCommissionPricingUseCase).execute();
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID02 [N]: Authenticated ADMIN retrieves summary configuration returning 200 OK")
    void getManagementPricing_adminRole_returns200Ok() throws Exception {
        ManagementCommissionPricingResponse responseDto = buildSampleManagementSummaryResponse();
        when(getManagementCommissionPricingUseCase.execute()).thenReturn(responseDto);

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tiers").isArray())
                .andExpect(jsonPath("$.data.rules").isArray());

        verify(getManagementCommissionPricingUseCase).execute();
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID03 [A]: Anonymous caller without token returns 401 UNAUTHORIZED")
    void getManagementPricing_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(getManagementCommissionPricingUseCase);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID04 [A]: Caller with CUSTOMER role returns 403 FORBIDDEN")
    void getManagementPricing_customerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagementCommissionPricingUseCase);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID05 [A]: Caller with STAFF role returns 403 FORBIDDEN")
    void getManagementPricing_staffRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagementCommissionPricingUseCase);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID06 [A]: Caller with DESIGNER role returns 403 FORBIDDEN")
    void getManagementPricing_designerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("5").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(getManagementCommissionPricingUseCase);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID07 [A]: UseCase throws unexpected RuntimeException returns 500 INTERNAL_SERVER_ERROR")
    void getManagementPricing_unexpectedException_returns500() throws Exception {
        when(getManagementCommissionPricingUseCase.execute())
                .thenThrow(new RuntimeException("Database connection failure"));

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        verify(getManagementCommissionPricingUseCase).execute();
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID08 [N]: GET /tiers/{tierId} returns 200 OK with full tier detail")
    void getManagementPricingTierDetail_returns200Ok() throws Exception {
        BundledOptionResponse bundledOption = BundledOptionResponse.builder()
                .optionId(1L)
                .ruleKey("FORMAT_TYPE")
                .configName("Format Type")
                .optionCode("STATIC_IMAGE")
                .optionLabel("Static PNG")
                .basePrice(BigDecimal.ZERO)
                .build();

        ManagementPricingTierResponse detail = ManagementPricingTierResponse.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .tierName("Gói Cơ Bản (Basic Package)")
                .description("Gói thiết kế tĩnh tiêu chuẩn")
                .basePrice(new BigDecimal("79000.00"))
                .customCalculatedPrice(new BigDecimal("100000.00"))
                .savingsAmount(new BigDecimal("21000.00"))
                .discountPercent(new BigDecimal("21.00"))
                .maxRevisions(1)
                .extraRevisionFeePercent(new BigDecimal("10.00"))
                .isCustom(false)
                .isActive(true)
                .displayOrder(1)
                .includedOptions(List.of(bundledOption))
                .includedOptionIds(List.of(1L))
                .build();

        when(getManagementPricingTierDetailUseCase.execute(1L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/tiers/1")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tierId").value(1))
                .andExpect(jsonPath("$.data.tierCode").value("BASIC"))
                .andExpect(jsonPath("$.data.includedOptions[0].optionCode").value("STATIC_IMAGE"))
                .andExpect(jsonPath("$.data.includedOptionIds[0]").value(1));

        verify(getManagementPricingTierDetailUseCase).execute(1L);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID09 [A]: GET /tiers/{tierId} returns 404 when tier not found")
    void getManagementPricingTierDetail_notFound_returns404() throws Exception {
        when(getManagementPricingTierDetailUseCase.execute(999L))
                .thenThrow(new CommissionTierNotFoundException("Commission pricing tier with ID 999 not found."));

        mockMvc.perform(get(BASE_PATH + "/tiers/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_TIER_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID10 [B]: GET /tiers/0 returns 400 Bad Request on non-positive tierId")
    void getManagementPricingTierDetail_invalidId_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/tiers/0")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getManagementPricingTierDetailUseCase);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID11 [N]: GET /rules/{ruleId} returns 200 OK with full rule detail")
    void getManagementPricingRuleDetail_returns200Ok() throws Exception {
        ManagementPricingOptionResponse option = ManagementPricingOptionResponse.builder()
                .optionId(1L)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Ảnh tĩnh (PNG)")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .isActive(true)
                .displayOrder(1)
                .build();

        ManagementPricingRuleResponse detail = ManagementPricingRuleResponse.builder()
                .configId(1L)
                .configCode("FORMAT_TYPE")
                .configName("Format Type")
                .description("Select format")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .isActive(true)
                .displayOrder(1)
                .options(List.of(option))
                .build();

        when(getManagementPricingRuleDetailUseCase.execute(1L)).thenReturn(detail);

        mockMvc.perform(get(BASE_PATH + "/rules/1")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configId").value(1))
                .andExpect(jsonPath("$.data.configCode").value("FORMAT_TYPE"))
                .andExpect(jsonPath("$.data.options[0].optionCode").value("STATIC_IMAGE"));

        verify(getManagementPricingRuleDetailUseCase).execute(1L);
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID12 [A]: GET /rules/{ruleId} returns 404 when rule not found")
    void getManagementPricingRuleDetail_notFound_returns404() throws Exception {
        when(getManagementPricingRuleDetailUseCase.execute(999L))
                .thenThrow(new CommissionRuleNotFoundException("Commission pricing rule with ID 999 not found."));

        mockMvc.perform(get(BASE_PATH + "/rules/999")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_RULE_NOT_FOUND"));
    }

    @Test
    @DisplayName("CMS-29 Controller UTCID13 [B]: GET /rules/-1 returns 400 Bad Request on negative ruleId")
    void getManagementPricingRuleDetail_invalidId_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH + "/rules/-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(getManagementPricingRuleDetailUseCase);
    }
}
