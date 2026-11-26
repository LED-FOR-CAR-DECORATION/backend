package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.api.response.ManagedBundleResponse;
import com.lumintra.backend.store.infrastructure.persistence.entity.BundleEntity;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ManagedBundleRepository extends JpaRepository<BundleEntity, Long> {
    @Query(
            value =
                    """
                    select new com.lumintra.backend.store.api.response.ManagedBundleResponse(
                        bundle.bundleId, bundle.productId, bundle.bundleName, bundle.description,
                        bundle.originalPrice, bundle.bundlePrice, bundle.status,
                        (select count(item) from BundleItemEntity item where item.bundle = bundle))
                    from BundleEntity bundle
                    where :status is null or bundle.status = :status
                    order by bundle.bundleId desc
                    """,
            countQuery =
                    """
                    select count(bundle) from BundleEntity bundle
                    where :status is null or bundle.status = :status
                    """)
    Page<ManagedBundleResponse> list(@Param("status") String status, Pageable pageable);
}
