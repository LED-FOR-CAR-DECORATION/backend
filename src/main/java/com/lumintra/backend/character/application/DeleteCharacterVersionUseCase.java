package com.lumintra.backend.character.application;

import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.shared.exception.character.CharacterVersionAlreadyLinkedToProductException;
import com.lumintra.backend.shared.exception.character.CharacterVersionNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class DeleteCharacterVersionUseCase {

    private static final Logger log = LoggerFactory.getLogger(DeleteCharacterVersionUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;

    @Transactional
    public void execute(String subject, Long characterId, Long versionId) {
        Long callerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);

        CharacterEntity character = characterRepository.findById(characterId)
                .orElseThrow(() -> {
                    log.warn("Character version deletion rejected: character not found [characterId={}] [versionId={}] [callerEmployeeId={}]",
                            characterId, versionId, callerEmployeeId);
                    return new CharacterNotFoundException(characterId);
                });

        if (!character.isOwnedBy(callerEmployeeId)) {
            log.warn("Character version deletion rejected: forbidden ownership mismatch [characterId={}] [versionId={}] [creatorEmployeeId={}] [callerEmployeeId={}]",
                    characterId, versionId, character.getCreatorEmployeeId(), callerEmployeeId);
            throw new AccessDeniedException("Access is denied. You can only delete versions of characters you own.");
        }

        CharacterVersionEntity version = characterVersionRepository.findById(versionId)
                .filter(v -> v.belongsToCharacter(characterId))
                .orElseThrow(() -> {
                    log.warn("Character version deletion rejected: version not found [characterId={}] [versionId={}] [callerEmployeeId={}]",
                            characterId, versionId, callerEmployeeId);
                    return new CharacterVersionNotFoundException(characterId, versionId);
                });

        if (version.isListedOnStore()) {
            log.warn("Character version deletion rejected: version already linked to store product [characterId={}] [versionId={}] [productId={}] [callerEmployeeId={}]",
                    characterId, versionId, version.getProductId(), callerEmployeeId);
            throw new CharacterVersionAlreadyLinkedToProductException(versionId, version.getProductId());
        }

        characterVersionRepository.delete(version);

        log.info("Character version deleted successfully [characterId={}] [versionId={}] [callerEmployeeId={}]",
                characterId, versionId, callerEmployeeId);
    }
}
