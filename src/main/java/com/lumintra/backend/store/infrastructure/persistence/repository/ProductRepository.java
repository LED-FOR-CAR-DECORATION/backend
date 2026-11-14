package com.lumintra.backend.store.infrastructure.persistence.repository;

import com.lumintra.backend.store.domain.model.ProductStatus;
import com.lumintra.backend.store.infrastructure.persistence.entity.ProductEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long>,
        JpaSpecificationExecutor<ProductEntity> {

    Optional<ProductEntity> findFirstByProductName(String productName);

    @EntityGraph(attributePaths = "tags")
    Optional<ProductEntity> findWithTagsByProductIdAndStatusIn(Long productId, Collection<ProductStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from ProductEntity product where product.productId = :id")
    Optional<ProductEntity> findForManagementUpdate(@Param("id") long id);
}
