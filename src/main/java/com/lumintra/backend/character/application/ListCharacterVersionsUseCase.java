package com.lumintra.backend.character.application;

import com.lumintra.backend.character.api.response.CharacterVersionResponse;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.mapper.CharacterVersionMapper;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.iam.domain.service.InternalEmployeeAccessService;
import com.lumintra.backend.shared.exception.character.CharacterNotFoundException;
import com.lumintra.backend.store.domain.model.ProductSnapshot;
import com.lumintra.backend.store.domain.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListCharacterVersionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ListCharacterVersionsUseCase.class);
    private static final Set<String> DESIGNER_ROLE = Set.of("DESIGNER");
    private static final Set<String> MANAGEMENT_ROLES = Set.of("MANAGER", "ADMIN");

    private final InternalEmployeeAccessService internalEmployeeAccessService;
    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;
    private final ProductLookupService productLookupService;
    private final CharacterVersionMapper characterVersionMapper;

    @Transactional(readOnly = true)
    public List<CharacterVersionResponse> execute(String subject, boolean isManagerOrAdmin, Long characterId) {
        Long callerEmployeeId;
        if (isManagerOrAdmin) {
            callerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, MANAGEMENT_ROLES);
        } else {
            callerEmployeeId = internalEmployeeAccessService.requireActiveEmployeeId(subject, DESIGNER_ROLE);
        }

        CharacterEntity character = characterRepository.findById(characterId)
                .orElseThrow(() -> {
                    log.warn("Character versions retrieval rejected: character not found [characterId={}] [callerEmployeeId={}]",
                            characterId, callerEmployeeId);
                    return new CharacterNotFoundException(characterId);
                });

        if (!isManagerOrAdmin && !character.isOwnedBy(callerEmployeeId)) {
            log.warn("Character versions retrieval rejected: forbidden ownership mismatch [characterId={}] [creatorEmployeeId={}] [callerEmployeeId={}]",
                    characterId, character.getCreatorEmployeeId(), callerEmployeeId);
            throw new AccessDeniedException("Access is denied. You can only inspect versions of characters created by your account.");
        }

        List<CharacterVersionEntity> versions = characterVersionRepository
                .findByCharacter_CharacterIdOrderByVersionNumberDesc(characterId);

        Set<Long> productIds = versions.stream()
                .map(CharacterVersionEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, ProductSnapshot> productSnapshots = productIds.isEmpty()
                ? Collections.emptyMap()
                : productLookupService.findSnapshotsByIds(productIds);

        List<CharacterVersionResponse> response = versions.stream()
                .map(version -> characterVersionMapper.toResponse(version, productSnapshots))
                .toList();

        log.info("Character versions listed successfully [characterId={}] [callerEmployeeId={}] [isManagerOrAdmin={}] [versionCount={}]",
                characterId, callerEmployeeId, isManagerOrAdmin, response.size());

        return response;
    }
}
