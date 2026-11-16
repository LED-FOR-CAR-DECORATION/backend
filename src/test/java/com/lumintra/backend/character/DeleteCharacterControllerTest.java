package com.lumintra.backend.character;

import com.lumintra.backend.character.api.controller.DeleteCharacterController;
import com.lumintra.backend.character.application.DeleteCharacterUseCase;
import com.lumintra.backend.iam.domain.service.TokenBlacklistService;
import com.lumintra.backend.iam.infrastructure.security.JwtTokenProvider;
import com.lumintra.backend.shared.exception.GlobalExceptionHandler;
import com.lumintra.backend.shared.exception.character.CannotDeleteListedCharacterException;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.shared.security.JwtAuthenticationFilter;
import com.lumintra.backend.shared.security.SecurityConfig;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeleteCharacterController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, GlobalExceptionHandler.class})
class DeleteCharacterControllerTest {

    private static final String BASE_PATH = "/api/management/characters/{id}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeleteCharacterUseCase deleteCharacterUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistService tokenBlacklistService;

    @MockitoBean
    private Tracer tracer;

    @Test
    @DisplayName("CHR-05 [N]: DESIGNER deletes unlisted character successfully -> 204 No Content")
    void deleteCharacter_whenValidDraftCharacter_shouldReturnNoContent() throws Exception {
        doNothing().when(deleteCharacterUseCase).execute("4", 12L);

        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNoContent());

        verify(deleteCharacterUseCase).execute("4", 12L);
    }

    @Test
    @DisplayName("CHR-05 [A]: MANAGER role rejected -> 403 FORBIDDEN")
    void deleteCharacter_whenUserIsManager_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("99").roles("MANAGER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-05 [A]: ADMIN role rejected -> 403 FORBIDDEN")
    void deleteCharacter_whenUserIsAdmin_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("1").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-05 [A]: STAFF role rejected -> 403 FORBIDDEN")
    void deleteCharacter_whenUserIsStaff_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("7").roles("STAFF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-05 [A]: CUSTOMER role rejected -> 403 FORBIDDEN")
    void deleteCharacter_whenUserIsCustomer_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("15").roles("CUSTOMER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verifyNoInteractions(deleteCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-05 [A]: Unauthenticated request rejected -> 401 UNAUTHORIZED")
    void deleteCharacter_whenUnauthenticated_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 12L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(deleteCharacterUseCase);
    }

    @Test
    @DisplayName("CHR-05 [A]: Character does not exist -> 404 CHARACTER_NOT_FOUND")
    void deleteCharacter_whenCharacterNotFound_shouldReturnNotFound() throws Exception {
        doThrow(new CharacterNotFoundException(999L))
                .when(deleteCharacterUseCase).execute("4", 999L);

        mockMvc.perform(delete(BASE_PATH, 999L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Character not found with id: 999"));
    }

    @Test
    @DisplayName("CHR-05 [A]: Caller is not creator of character (BR-FL5-07) -> 403 FORBIDDEN")
    void deleteCharacter_whenNotOwner_shouldReturnForbidden() throws Exception {
        doThrow(new AccessDeniedException("Access is denied."))
                .when(deleteCharacterUseCase).execute("4", 12L);

        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("CHR-05 [A]: Character has versions listed on Store (BR-FL5-09) -> 409 CANNOT_DELETE_LISTED_CHARACTER")
    void deleteCharacter_whenCharacterListedOnStore_shouldReturnConflict() throws Exception {
        doThrow(new CannotDeleteListedCharacterException(12L))
                .when(deleteCharacterUseCase).execute("4", 12L);

        mockMvc.perform(delete(BASE_PATH, 12L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_LISTED_CHARACTER"))
                .andExpect(jsonPath("$.message").value("Cannot delete character with id 12 because one or more versions are listed on the Store."));
    }

    @Test
    @DisplayName("CHR-05 [B]: Non-positive character ID in path rejected -> 400 VALIDATION_FAILED")
    void deleteCharacter_whenIdIsNonPositive_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(delete(BASE_PATH, 0L)
                        .with(SecurityMockMvcRequestPostProcessors.user("4").roles("DESIGNER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(deleteCharacterUseCase);
    }
}
