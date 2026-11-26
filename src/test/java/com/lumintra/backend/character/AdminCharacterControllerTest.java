package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.AdminCharacterController;
import com.lumintra.backend.character.api.response.AdminCharacterItem;
import com.lumintra.backend.character.application.ListAllCharactersUseCase;
import com.lumintra.backend.character.domain.model.CharacterType;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class AdminCharacterControllerTest {

    private static final String BASE_PATH = "/api/management/admin/characters";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListAllCharactersUseCase listAllCharactersUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private AdminCharacterItem sampleItem() {
        return new AdminCharacterItem(
                10L,
                "Cyber Fox",
                "Mascot pixel cyberpunk",
                CharacterType.STORE,
                null,
                new AdminCharacterItem.AuthorSummary(4L, "Test Designer", "designer@lumintra.com"),
                3,
                Instant.parse("2026-11-14T08:30:00Z")
        );
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID01 [N]: Authenticated MANAGER retrieves character catalog returning 200 OK")
    void listAllCharacters_managerRole_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleItem()), PageRequest.of(0, 10), 1);
        when(listAllCharactersUseCase.execute(any())).thenReturn(page);

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].characterId").value(10))
                .andExpect(jsonPath("$.data[0].characterName").value("Cyber Fox"))
                .andExpect(jsonPath("$.data[0].description").value("Mascot pixel cyberpunk"))
                .andExpect(jsonPath("$.data[0].author.employeeId").value(4))
                .andExpect(jsonPath("$.data[0].author.fullName").value("Test Designer"))
                .andExpect(jsonPath("$.data[0].author.email").value("designer@lumintra.com"))
                .andExpect(jsonPath("$.data[0].totalVersions").value(3))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.totalPages").value(1));
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID02 [N]: Authenticated ADMIN retrieves character catalog returning 200 OK")
    void listAllCharacters_adminRole_returns200() throws Exception {
        var page = new PageImpl<>(List.of(sampleItem()), PageRequest.of(0, 10), 1);
        when(listAllCharactersUseCase.execute(any())).thenReturn(page);

        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("ADMIN"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].characterId").value(10))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID03 [A]: Caller with DESIGNER role receives 403 FORBIDDEN")
    void listAllCharacters_designerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID04 [A]: Caller with CUSTOMER role receives 403 FORBIDDEN")
    void listAllCharacters_customerRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("3").roles("CUSTOMER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID05 [A]: Caller with STAFF role receives 403 FORBIDDEN")
    void listAllCharacters_staffRole_returns403() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID06 [A]: Unauthenticated request receives 401 UNAUTHORIZED")
    void listAllCharacters_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID07 [A]: Negative page parameter returns 400 VALIDATION_FAILED")
    void listAllCharacters_negativePage_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("page", "-1")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID08 [A]: Zero or negative size parameter returns 400 VALIDATION_FAILED")
    void listAllCharacters_zeroOrNegativeSize_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("size", "0")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID09 [A]: Size parameter exceeding 100 returns 400 VALIDATION_FAILED")
    void listAllCharacters_sizeExceeding100_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("size", "101")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listAllCharactersUseCase);
    }

    @Test
    @DisplayName("CHR-02 Controller UTCID10 [A]: Non-positive creatorEmployeeId parameter returns 400 VALIDATION_FAILED")
    void listAllCharacters_negativeCreatorEmployeeId_returns400() throws Exception {
        mockMvc.perform(get(BASE_PATH)
                        .param("creatorEmployeeId", "-5")
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(listAllCharactersUseCase);
    }
}
