package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.DesignerCharacterController;
import com.lumintra.backend.character.api.response.DesignerCharacterItem;
import com.lumintra.backend.character.application.ListMyCharactersUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DesignerCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DesignerCharacterControllerTest {

    private static final String BASE_PATH = "/api/management/designer/characters/my";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListMyCharactersUseCase listMyCharactersUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private DesignerCharacterItem sampleItem() {
        return new DesignerCharacterItem(
                10L,
                "Cyber Fox",
                "Mascot pixel cyberpunk",
                3,
                new DesignerCharacterItem.CurrentVersionSummary(
                        25L,
                        3,
                        "https://cdn.lumintra.com/characters/cyber-fox-v3.gif"
                ),
                Instant.parse("2026-11-14T08:30:00Z")
        );
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID01 [N]: Authenticated DESIGNER retrieves character catalog returning 200 OK")
    void listMyCharacters_designerRole_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleItem()), PageRequest.of(0, 10), 1);
        when(listMyCharactersUseCase.execute(eq("42"), any())).thenReturn(page);

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].characterId").value(10))
                .andExpect(jsonPath("$.data[0].characterName").value("Cyber Fox"))
                .andExpect(jsonPath("$.data[0].description").value("Mascot pixel cyberpunk"))
                .andExpect(jsonPath("$.data[0].totalVersions").value(3))
                .andExpect(jsonPath("$.data[0].currentVersion.versionId").value(25))
                .andExpect(jsonPath("$.data[0].currentVersion.versionNumber").value(3))
                .andExpect(jsonPath("$.data[0].currentVersion.previewGifUrl").value("https://cdn.lumintra.com/characters/cyber-fox-v3.gif"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID02 [A]: Caller with CUSTOMER role receives 403 FORBIDDEN")
    void listMyCharacters_customerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listMyCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID03 [A]: Caller with STAFF role receives 403 FORBIDDEN")
    void listMyCharacters_staffRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listMyCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID04 [A]: Unauthenticated request receives 401 UNAUTHORIZED")
    void listMyCharacters_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listMyCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID05 [A]: Negative page parameter returns 400 VALIDATION_FAILED")
    void listMyCharacters_negativePage_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("page", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listMyCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID06 [A]: Zero or negative size parameter returns 400 VALIDATION_FAILED")
    void listMyCharacters_zeroOrNegativeSize_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("size", "0")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listMyCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-01 Controller UTCID07 [A]: Size parameter exceeding 100 returns 400 VALIDATION_FAILED")
    void listMyCharacters_sizeExceeding100_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("size", "101")
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listMyCharactersUseCase);
    }
}
