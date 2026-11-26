package com.lumintra.backend.store.application;

import com.lumintra.backend.shared.exception.store.PlanCodeAlreadyExistsException;
import com.lumintra.backend.shared.exception.store.StoreProductTagNotFoundException;
import com.lumintra.backend.store.api.request.CreateSubscriptionRequest;
import com.lumintra.backend.store.api.response.CreatedSubscriptionResponse;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.TagRepository;

import lombok.RequiredArgsConstructor;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CreateSubscriptionUseCase {
    private final ProductRepository products;
    private final SubscriptionDetailRepository subscriptions;
    private final TagRepository tags;

    @Transactional
    public CreatedSubscriptionResponse execute(CreateSubscriptionRequest request) {
        if (subscriptions.findByPlanCode(request.planCode()).isPresent()) {
            throw new PlanCodeAlreadyExistsException();
        }

        var ids =
                request.tagIds() == null
                        ? List.<Long>of()
                        : request.tagIds().stream().distinct().toList();
        var selectedTags = tags.findAllById(ids);
        if (selectedTags.size() != ids.size()) {
            throw new StoreProductTagNotFoundException();
        }

        var product =
                products.save(
                        ProductEntity.create(
                                request.planName(),
                                ProductType.SUBSCRIPTION,
                                request.basePrice(),
                                request.description(),
                                request.thumbnailUrl(),
                                request.galleryUrls() == null ? List.of() : request.galleryUrls(),
                                ProductStatus.UNPUBLISHED,
                                new HashSet<>(selectedTags)));
        int bonusRevisions =
                request.maxCommissionRevisions() == null ? 0 : request.maxCommissionRevisions();
        int maxMobileProfiles =
                request.maxMobileProfiles() == null ? 3 : request.maxMobileProfiles();
        String perksDescription =
                buildPerksDescription(
                        request.allAccessCharacters(),
                        maxMobileProfiles,
                        bonusRevisions);
        var detail =
                SubscriptionDetailEntity.create(
                        product.getProductId(),
                        request.planCode(),
                        request.planName(),
                        request.lifetime(),
                        request.durationDays(),
                        request.allAccessCharacters(),
                        bonusRevisions,
                        maxMobileProfiles,
                        Map.of(),
                        perksDescription);
        try {
            subscriptions.saveAndFlush(detail);
        } catch (DataIntegrityViolationException ex) {
            if (ex.getCause() instanceof ConstraintViolationException violation
                    && ("subscription_detail_plan_code_key".equals(violation.getConstraintName())
                            || "uq_subscription_detail_plan_code"
                                    .equals(violation.getConstraintName()))) {
                throw new PlanCodeAlreadyExistsException();
            }
            throw ex;
        }

        return new CreatedSubscriptionResponse(
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
                detail.getMaxCommissionRevisions(),
                detail.getMaxMobileProfiles(),
                product.getStatus(),
                detail.getCreatedAt(),
                ids);
    }

    private String buildPerksDescription(
            boolean allAccessCharacters,
            int maxMobileProfiles,
            int maxCommissionRevisions) {
        var parts = new java.util.ArrayList<String>();
        if (allAccessCharacters) {
            parts.add("Mở khóa toàn bộ thư viện nhân vật");
        }
        if (maxMobileProfiles > 3) {
            parts.add("Tối đa " + maxMobileProfiles + " profile chữ trên ứng dụng Mobile");
        }
        if (maxCommissionRevisions > 0) {
            parts.add(maxCommissionRevisions + " lượt chỉnh sửa miễn phí cho mỗi đơn thiết kế");
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
