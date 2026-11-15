package com.lumintra.backend.character.application;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CannotDeleteListedCharacterException;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeleteCharacterUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteCharacterUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;

    @Transactional
    public void execute(String subject, Long characterId) {
        Long callerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        CharacterEntity character = characterRepository.findById(characterId)
                .orElseThrow(() -> {
                    log.warn("Character deletion rejected: character not found [characterId={}] [callerEmployeeId={}]",
                            characterId, callerEmployeeId);
                    return new CharacterNotFoundException(characterId);
                });

        if (!character.isOwnedBy(callerEmployeeId)) {
            log.warn("Character deletion rejected: forbidden ownership mismatch [characterId={}] [creatorEmployeeId={}] [callerEmployeeId={}]",
                    characterId, character.getCreatorEmployeeId(), callerEmployeeId);
            throw new AccessDeniedException("Access is denied.");
        }

        List<CharacterVersionEntity> versions = characterVersionRepository.findByCharacter_CharacterId(characterId);
        boolean hasListedVersion = versions.stream().anyMatch(version -> version.getProductId() != null);

        if (hasListedVersion) {
            log.warn("Character deletion rejected: character has versions listed on Store [characterId={}] [callerEmployeeId={}]",
                    characterId, callerEmployeeId);
            throw new CannotDeleteListedCharacterException(characterId);
        }

        if (!versions.isEmpty()) {
            characterVersionRepository.deleteAll(versions);
        }
        characterRepository.delete(character);

        log.info("Graphic character and unlisted versions deleted successfully [characterId={}] [callerEmployeeId={}] [versionsDeleted={}]",
                characterId, callerEmployeeId, versions.size());
    }
}
