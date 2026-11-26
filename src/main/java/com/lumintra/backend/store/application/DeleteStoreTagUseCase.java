package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.domain.event.StoreTagUpdatedEvent;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagDeletionRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteStoreTagUseCase {
    private final TagDeletionRepository deletion;
    private final TagRepository tags;
    private final ApplicationEventPublisher events;

    @Transactional
    public void execute(long id) {
        var tag = deletion.findForDeletion(id).orElseThrow(StoreProductTagNotFoundException::new);
        deletion.detachFromProducts(id);
        tags.delete(tag);
        events.publishEvent(new StoreTagUpdatedEvent());
    }
}
