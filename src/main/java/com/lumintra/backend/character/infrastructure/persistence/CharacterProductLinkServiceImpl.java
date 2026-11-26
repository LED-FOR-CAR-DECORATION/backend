package com.lumintra.backend.character.infrastructure.persistence;

import com.lumintra.backend.character.domain.service.CharacterProductLinkService;
import com.lumintra.backend.character.infrastructure.persistence.repository.CharacterVersionRepository;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException;
import com.lumintra.backend.shared.exception.store.StoreProductReferenceException.Reason;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;

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
        link(versionId, productId, null);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void linkOwnedVersion(long versionId, long productId, long designerEmployeeId) {
        link(versionId, productId, designerEmployeeId);
    }

    private void link(long versionId, long productId, Long designerEmployeeId) {
        var version =
                versions.findForProductLink(versionId)
                        .orElseThrow(
                                () ->
                                        new StoreProductReferenceException(
                                                Reason.CHARACTER_VERSION_NOT_FOUND,
                                                "Character version not found."));
        if (designerEmployeeId != null && !version.getCharacter().isOwnedBy(designerEmployeeId)) {
            throw new AccessDeniedException("Only the designer who owns this character can submit it for sale.");
        }
        if (version.getCharacter() == null || !version.getCharacter().isStoreCharacter()) {
            throw new StoreProductReferenceException(
                    Reason.CHARACTER_TYPE_NOT_ALLOWED_FOR_STORE,
                    "Only characters with type STORE can be linked to a store product.");
        }
        if (version.getProductId() != null) {
            throw new StoreProductReferenceException(
                    Reason.CHARACTER_VERSION_ALREADY_LINKED,
                    "Character version already belongs to a product.");
        }
        version.setProductId(productId);
    }
}
