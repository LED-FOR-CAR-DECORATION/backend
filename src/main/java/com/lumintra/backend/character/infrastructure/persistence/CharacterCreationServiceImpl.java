package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.service.CharacterCreationService;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterRepository;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterCreationServiceImpl implements CharacterCreationService {

    private final CharacterRepository characterRepository;
    private final CharacterVersionRepository characterVersionRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long createForProduct(
            long productId,
            String characterName,
            String description,
            int versionNumber,
            String previewGifUrl,
            String ledBinaryUrl,
            Integer frameCount,
            Integer frameDelayMs,
            Integer byteSize) {
        CharacterEntity character = characterRepository.save(
                CharacterEntity.create(null, characterName, description));

        CharacterVersionEntity version = CharacterVersionEntity.create(
                character,
                productId,
                versionNumber,
                previewGifUrl,
                ledBinaryUrl,
                true);
        version.setFrameCount(frameCount);
        version.setFrameDelayMs(frameDelayMs);
        version.setByteSize(byteSize);

        return characterVersionRepository.save(version).getVersionId();
    }
}
