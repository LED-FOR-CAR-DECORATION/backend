package com.lumintra.backend.commission;

import com.lumintra.backend.commission.api.controller.CommissionPricingController;
import com.lumintra.backend.commission.api.response.PricingOptionResponse;
import com.lumintra.backend.commission.api.response.PricingRuleResponse;
import com.lumintra.backend.commission.api.response.PricingTierSummaryResponse;
import com.lumintra.backend.commission.api.response.PublicCommissionPricingResponse;
import com.lumintra.backend.commission.application.GetPublicCommissionPricingUseCase;
import com.lumintra.backend.commission.domain.model.SelectionType;
import com.lumintra.backend.commission.domain.model.UiType;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CommissionPricingController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        GlobalExceptionHandler.class
})
@ActiveProfiles("test")
class CommissionPricingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPublicCommissionPricingUseCase getPublicCommissionPricingUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("CMS-01 Controller UTCID01 [N]: Public GET /api/commissions/pricing returns 200 OK with pricing tier summaries and rules")
    void getCommissionPricing_returns200OkWithTiersAndRules() throws Exception {
        PricingTierSummaryResponse basicTier = PricingTierSummaryResponse.builder()
                .tierId(1L)
                .tierCode("BASIC")
                .tierName("Gói Cơ Bản (Basic Package)")
                .description("Gói thiết kế tĩnh tiêu chuẩn")
                .basePrice(new BigDecimal("79000.00"))
                .customCalculatedPrice(new BigDecimal("100000.00"))
                .savingsAmount(new BigDecimal("21000.00"))
                .maxRevisions(1)
                .isCustom(false)
                .displayOrder(1)
                .bundledOptionIds(List.of(1L))
                .build();

        PricingOptionResponse optionResponse = PricingOptionResponse.builder()
                .optionId(1L)
                .optionCode("STATIC_IMAGE")
                .optionLabel("Ảnh tĩnh (PNG)")
                .basePrice(BigDecimal.ZERO)
                .isDefault(true)
                .displayOrder(1)
                .build();

        PricingRuleResponse ruleResponse = PricingRuleResponse.builder()
                .configId(1L)
                .ruleKey("FORMAT_TYPE")
                .configName("Định dạng hiển thị (Format Type)")
                .description("Chọn định dạng ảnh tĩnh hoặc ảnh động")
                .selectionType(SelectionType.SINGLE_SELECT)
                .uiType(UiType.RADIO)
                .isRequired(true)
                .isSystem(true)
                .displayOrder(1)
                .options(List.of(optionResponse))
                .build();

        PublicCommissionPricingResponse responseDto = PublicCommissionPricingResponse.builder()
                .tiers(List.of(basicTier))
                .rules(List.of(ruleResponse))
                .build();

        when(getPublicCommissionPricingUseCase.execute()).thenReturn(responseDto);

        mockMvc.perform(get("/api/commissions/pricing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tiers[0].tierCode").value("BASIC"))
                .andExpect(jsonPath("$.data.tiers[0].basePrice").value(79000.00))
                .andExpect(jsonPath("$.data.tiers[0].customCalculatedPrice").value(100000.00))
                .andExpect(jsonPath("$.data.tiers[0].savingsAmount").value(21000.00))
                .andExpect(jsonPath("$.data.tiers[0].bundledOptionIds[0]").value(1))
                .andExpect(jsonPath("$.data.rules[0].ruleKey").value("FORMAT_TYPE"))
                .andExpect(jsonPath("$.data.rules[0].options[0].optionCode").value("STATIC_IMAGE"));

        verify(getPublicCommissionPricingUseCase).execute();
    }

    @Test
    @DisplayName("CMS-01 Controller UTCID02 [A]: GET /api/commissions/pricing returns 404 NOT_FOUND when CUSTOM tier missing")
    void getCommissionPricing_returns404_whenCustomTierNotFound() throws Exception {
        when(getPublicCommissionPricingUseCase.execute())
                .thenThrow(new CommissionTierNotFoundException("Active CUSTOM tier configuration not found."));

        mockMvc.perform(get("/api/commissions/pricing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMISSION_TIER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Active CUSTOM tier configuration not found."));

        verify(getPublicCommissionPricingUseCase).execute();
    }

    @Test
    @DisplayName("CMS-01 Controller UTCID03 [A]: GET /api/commissions/pricing returns 500 INTERNAL_SERVER_ERROR on unexpected failure")
    void getCommissionPricing_returns500_onUnexpectedFailure() throws Exception {
        when(getPublicCommissionPricingUseCase.execute())
                .thenThrow(new RuntimeException("Database connection failure"));

        mockMvc.perform(get("/api/commissions/pricing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));

        verify(getPublicCommissionPricingUseCase).execute();
    }
}
