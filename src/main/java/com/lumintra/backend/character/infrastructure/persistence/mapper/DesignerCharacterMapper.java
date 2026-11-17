package com.lumintra.backend.character.infrastructure.persistence.mapper;

import com.lumintra.backend.character.api.response.DesignerCharacterItem;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterEntity;
import com.lumintra.backend.character.infrastructure.persistence.entity.CharacterVersionEntity;

import java.util.Comparator;
import java.util.List;

public final class DesignerCharacterMapper {

    private DesignerCharacterMapper() {
    }

    public static DesignerCharacterItem toItem(CharacterEntity character, List<CharacterVersionEntity> versions) {
        int totalVersions = versions != null ? versions.size() : 0;
        DesignerCharacterItem.CurrentVersionSummary currentVersionSummary = null;

        if (versions != null && !versions.isEmpty()) {
            // Luôn luôn chọn version có versionNumber lớn nhất (bản mới nhất) để hiển thị thumbnail
            CharacterVersionEntity latest = versions.stream()
                    .max(Comparator.comparingInt(CharacterVersionEntity::getVersionNumber))
                    .orElse(versions.get(0));

            currentVersionSummary = new DesignerCharacterItem.CurrentVersionSummary(
                    latest.getVersionId(),
                    latest.getVersionNumber(),
                    latest.getPreviewGifUrl()
            );
        }

        return new DesignerCharacterItem(
                character.getCharacterId(),
                character.getCharacterName(),
                character.getDescription(),
                totalVersions,
                currentVersionSummary,
                character.getCreatedAt()
        );
    }
}
