package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.store.api.request.UpdateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.domain.event.StoreTagUpdatedEvent;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateStoreTagUseCase {
    private final TagRepository tags;
    private final ApplicationEventPublisher events;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoreTagResponse execute(long id, UpdateStoreTagRequest request) {
        tags.lockForCreation();
        var tag = tags.findById(id).orElseThrow(StoreProductTagNotFoundException::new);
        if (tags.existsByNormalizedNameExcluding(request.name(), id)) {
            throw new TagAlreadyExistsException();
        }

        tag.setName(request.name());
        tag.setDescription(request.description());
        events.publishEvent(new StoreTagUpdatedEvent());
        return new StoreTagResponse(tag.getTagId(), tag.getName(), tag.getDescription());
    }
}
