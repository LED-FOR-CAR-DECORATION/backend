package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.TagAlreadyExistsException;
import com.lumintra.backend.store.api.request.CreateStoreTagRequest;
import com.lumintra.backend.store.api.response.StoreTagResponse;
import com.lumintra.backend.store.domain.event.StoreTagCreatedEvent;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateStoreTagUseCase {
    private final TagRepository tags;
    private final ApplicationEventPublisher events;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public StoreTagResponse execute(CreateStoreTagRequest request) {
        tags.lockForCreation();
        if (tags.existsByNormalizedName(request.name())) {
            throw new TagAlreadyExistsException();
        }

        var tag = tags.save(TagEntity.create(request.name(), request.description()));
        events.publishEvent(new StoreTagCreatedEvent());
        return new StoreTagResponse(tag.getTagId(), tag.getName(), tag.getDescription());
    }
}
