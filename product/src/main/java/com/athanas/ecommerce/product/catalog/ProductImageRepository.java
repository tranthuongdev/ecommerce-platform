package com.athanas.ecommerce.product.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductId(UUID productId);

    @Query("SELECT pi FROM ProductImage pi WHERE pi.product.id IN :productIds AND pi.isPrimary = true")
    List<ProductImage> findPrimaryByProductIds(@Param("productIds") Collection<UUID> productIds);
}
