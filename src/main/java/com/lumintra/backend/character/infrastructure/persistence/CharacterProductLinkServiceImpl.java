package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.service.CharacterProductLinkService;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CharacterProductLinkServiceImpl implements CharacterProductLinkService {
    private final CharacterVersionRepository versions;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkVersion(long versionId, long productId) {
        var version =
                versions.findForProductLink(versionId)
                        .orElseThrow(
                                () ->
                                        new StoreProductReferenceException(
                                                Reason.CHARACTER_VERSION_NOT_FOUND,
                                                "Character version not found."));
        if (version.getProductId() != null) {
            throw new StoreProductReferenceException(
                    Reason.CHARACTER_VERSION_ALREADY_LINKED,
                    "Character version already belongs to a product.");
        }
        version.setProductId(productId);
    }
}
