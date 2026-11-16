package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.CreateCharacterController;
import com.lumintra.backend.character.api.response.CreateCharacterResponse;
import com.lumintra.backend.character.application.CreateCharacterUseCase;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CreateCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class CreateCharacterControllerTest {

    private static final String BASE_PATH = "/api/management/characters";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateCharacterUseCase createCharacterUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    private CreateCharacterResponse sampleResponse() {
        return new CreateCharacterResponse(
                12L,
                "Neon Samurai",
                "Chiến binh samurai phong cách neon retro 8-bit",
                4L,
                Instant.parse("2026-11-15T02:00:00Z")
        );
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID01 [N]: Authenticated DESIGNER creates character returning 201 CREATED")
    void createCharacter_designerRole_returns201() throws Exception {
        CreateCharacterResponse response = sampleResponse();
        when(createCharacterUseCase.execute(eq("42"), any())).thenReturn(response);

        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Neon Samurai",
                                  "description": "Chiến binh samurai phong cách neon retro 8-bit"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.characterId").value(12))
                .andExpect(jsonPath("$.data.characterName").value("Neon Samurai"))
                .andExpect(jsonPath("$.data.description").value("Chiến binh samurai phong cách neon retro 8-bit"))
                .andExpect(jsonPath("$.data.creatorEmployeeId").value(4))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-15T02:00:00Z"));
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID02 [A]: Caller with MANAGER role receives 403 FORBIDDEN")
    void createCharacter_managerRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("MANAGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Manager Samurai"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID03 [A]: Caller with ADMIN role receives 403 FORBIDDEN")
    void createCharacter_adminRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("2").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Admin Samurai"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID04 [A]: Caller with STAFF role receives 403 FORBIDDEN")
    void createCharacter_staffRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Staff Samurai"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID05 [A]: Caller with CUSTOMER role receives 403 FORBIDDEN")
    void createCharacter_customerRole_returns403() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("9").roles("CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Customer Samurai"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID06 [A]: Unauthenticated request receives 401 UNAUTHORIZED")
    void createCharacter_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "Guest Samurai"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID07 [A]: Blank characterName returns 400 VALIDATION_FAILED")
    void createCharacter_blankName_returns400() throws Exception {
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "   ",
                                  "description": "Valid description"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-03 Controller UTCID08 [A]: CharacterName exceeding 255 chars returns 400 VALIDATION_FAILED")
    void createCharacter_nameExceeding255Chars_returns400() throws Exception {
        String longName = "A".repeat(256);
        mockMvc.perform(post(BASE_PATH)
                        .with(SecurityMockMvcRequestPostProcessors.user("42").roles("DESIGNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterName": "%s"
                                }
                                """.formatted(longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(createCharacterUseCase);
    }
}
