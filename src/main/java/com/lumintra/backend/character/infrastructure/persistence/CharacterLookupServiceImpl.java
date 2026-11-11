package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CharacterLookupServiceImpl implements CharacterLookupService {

    private final CharacterVersionRepository characterVersionRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findVersionIdByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return characterVersionRepository.findFirstByProductIdAndIsCurrentTrue(productId)
                .or(() -> characterVersionRepository.findFirstByProductIdOrderByVersionNumberDesc(productId))
                .map(CharacterVersionEntity::getVersionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findPreviewGifUrlsByProductIds(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<CharacterVersionEntity> versions =
                characterVersionRepository.findByProductIdInOrderByIsCurrentDescVersionNumberDesc(productIds);
        Map<Long, String> result = new HashMap<>();
        for (CharacterVersionEntity version : versions) {
            if (version.getProductId() != null && version.getPreviewGifUrl() != null) {
                result.putIfAbsent(version.getProductId(), version.getPreviewGifUrl());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CharacterVersionSnapshot> findDetailByProductId(Long productId) {
        if (productId == null) {
            return Optional.empty();
        }
        return characterVersionRepository.findWithCharacterFirstByProductIdAndIsCurrentTrue(productId)
                .or(() -> characterVersionRepository.findWithCharacterFirstByProductIdOrderByVersionNumberDesc(productId))
                .map(this::toSnapshot);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, CharacterVersionSnapshot> findDetailsByVersionIds(Collection<Long> versionIds) {
        if (versionIds == null || versionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return characterVersionRepository.findWithCharacterByVersionIdIn(versionIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(
                        CharacterVersionSnapshot::versionId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));
    }

    private CharacterVersionSnapshot toSnapshot(CharacterVersionEntity version) {
        CharacterEntity character = version.getCharacter();
        return new CharacterVersionSnapshot(
                version.getVersionId(),
                character != null ? character.getCharacterId() : null,
                character != null ? character.getCharacterName() : null,
                character != null ? character.getDescription() : null,
                version.getVersionNumber(),
                version.getPreviewGifUrl(),
                version.getFrameCount(),
                version.getFrameDelayMs()
        );
    }
}
