package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.InvalidSubscriptionDurationException;
import com.lumintra.backend.shared.exception.store.SubscriptionNotFoundException;
import com.lumintra.backend.store.api.request.UpdateSubscriptionRequest;
import com.lumintra.backend.store.api.response.UpdatedSubscriptionResponse;
import com.lumintra.backend.store.domain.event.StoreProductUpdatedEvent;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.TagEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateSubscriptionUseCase {
    private final ProductRepository products;
    private final SubscriptionDetailRepository subscriptions;
    private final ApplicationEventPublisher events;

    @Transactional
    public UpdatedSubscriptionResponse execute(long id, UpdateSubscriptionRequest request) {
        var detail = subscriptions.findById(id).orElseThrow(SubscriptionNotFoundException::new);
        var product =
                products.findForManagementUpdate(detail.getProductId())
                        .filter(
                                candidate ->
                                        candidate.getProductType() == ProductType.SUBSCRIPTION
                                                && candidate.getStatus() != ProductStatus.DELETED)
                        .orElseThrow(SubscriptionNotFoundException::new);
        if (detail.isLifetime() != (request.durationDays() == null)) {
            throw new InvalidSubscriptionDurationException();
        }

        detail.setPlanName(request.planName());
        detail.setDurationDays(request.durationDays());
        detail.setAllAccessCharacters(request.allAccessCharacters());
        product.setProductName(request.planName());
        product.setBasePrice(request.basePrice());
        product.setDescription(request.description());
        product.setThumbnailUrl(request.thumbnailUrl());
        events.publishEvent(new StoreProductUpdatedEvent(product.getProductId()));

        return new UpdatedSubscriptionResponse(
                detail.getSubscriptionDetailId(),
                product.getProductId(),
                detail.getPlanCode(),
                detail.getPlanName(),
                product.getBasePrice(),
                product.getDescription(),
                product.getThumbnailUrl(),
                detail.isLifetime(),
                detail.getDurationDays(),
                detail.isAllAccessCharacters(),
                product.getStatus(),
                detail.getCreatedAt(),
                product.getTags().stream().map(TagEntity::getTagId).sorted().toList());
    }
}
