package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
