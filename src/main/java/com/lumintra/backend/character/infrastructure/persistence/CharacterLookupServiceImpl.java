package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
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
}
