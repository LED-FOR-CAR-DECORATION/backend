package com.lumintra.backend.store.application;

import com.lumintra.backend.character.domain.model.CharacterVersionSnapshot;
import com.lumintra.backend.character.domain.service.CharacterLookupService;
import com.lumintra.backend.device.domain.model.LedModelSnapshot;
import com.lumintra.backend.device.domain.service.LedModelLookupService;
import com.lumintra.backend.shared.exception.store.ProductNotFoundException;
import com.lumintra.backend.store.api.response.ProductDetailResponse;
import com.lumintra.backend.store.domain.model.BundleItemType;
import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.domain.model.ProductType;
import com.lumintra.backend.store.infrastructure.caching.StoreCacheNames;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleItemEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import com.lumintra.backend.store.infrastructure.persistence.entity.SubscriptionDetailEntity;
import com.lumintra.backend.store.infrastructure.persistence.mapper.ProductMapper;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleItemRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.BundleRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.ProductRepository;
import com.lumintra.backend.store.infrastructure.persistence.repository.SubscriptionDetailRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetProductDetailUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetProductDetailUseCase.class);
    private static final Set<ProductStatus> PUBLIC_VIEWABLE_STATUSES =
            EnumSet.of(ProductStatus.PUBLISHED, ProductStatus.ARCHIVED);

    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final BundleItemRepository bundleItemRepository;
    private final SubscriptionDetailRepository subscriptionDetailRepository;
    private final LedModelLookupService ledModelLookupService;
    private final CharacterLookupService characterLookupService;
    private final ProductMapper productMapper;

    @Cacheable(value = StoreCacheNames.PRODUCT, key = "#productId", unless = "#result == null")
    @Transactional(readOnly = true)
    public ProductDetailResponse execute(Long productId) {
        if (productId == null || productId <= 0) {
            log.warn("Invalid product identifier requested [productId={}]", productId);
            throw new ProductNotFoundException("Product not found.");
        }

        ProductEntity product = productRepository
                .findWithTagsByProductIdAndStatusIn(productId, PUBLIC_VIEWABLE_STATUSES)
                .filter(ProductEntity::isPubliclyViewable)
                .orElseThrow(() -> {
                    log.warn("Product detail not found or unavailable [productId={}]", productId);
                    return new ProductNotFoundException("Product not found.");
                });

        LedModelSnapshot ledModel = null;
        CharacterVersionSnapshot characterVersion = null;
        BundleEntity bundle = null;
        List<BundleItemEntity> bundleItems = Collections.emptyList();
        Map<Long, LedModelSnapshot> bundleLedModels = Collections.emptyMap();
        Map<Long, CharacterVersionSnapshot> bundleCharacterVersions = Collections.emptyMap();
        SubscriptionDetailEntity subscriptionDetail = null;

        ProductType productType = product.getProductType();
        if (productType == ProductType.LED) {
            ledModel = ledModelLookupService.findByModelId(product.getProductId()).orElse(null);
        } else if (productType == ProductType.CHARACTER) {
            characterVersion = characterLookupService.findDetailByProductId(product.getProductId()).orElse(null);
        } else if (productType == ProductType.BUNDLE) {
            bundle = bundleRepository.findByProductId(product.getProductId()).orElse(null);
            if (bundle != null) {
                bundleItems = bundleItemRepository.findAllByBundle_BundleIdOrderByBundleItemIdAsc(bundle.getBundleId());

                Set<Long> modelIds = bundleItems.stream()
                        .filter(item -> item.getItemType() == BundleItemType.LED && item.getModelId() != null)
                        .map(BundleItemEntity::getModelId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                Set<Long> versionIds = bundleItems.stream()
                        .filter(item -> item.getItemType() == BundleItemType.CHARACTER && item.getVersionId() != null)
                        .map(BundleItemEntity::getVersionId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));

                bundleLedModels = modelIds.isEmpty()
                        ? Collections.emptyMap()
                        : ledModelLookupService.findByModelIds(modelIds);

                bundleCharacterVersions = versionIds.isEmpty()
                        ? Collections.emptyMap()
                        : characterLookupService.findDetailsByVersionIds(versionIds);
            }
        } else if (productType == ProductType.SUBSCRIPTION) {
            subscriptionDetail = subscriptionDetailRepository.findByProductId(product.getProductId()).orElse(null);
        }

        ProductDetailResponse response = productMapper.toProductDetailResponse(
                product,
                ledModel,
                characterVersion,
                bundle,
                bundleItems,
                bundleLedModels,
                bundleCharacterVersions,
                subscriptionDetail
        );

        log.info("Product detail retrieved [productId={}] [productType={}] [status={}]",
                productId, productType, product.getStatus());

        return response;
    }
}
