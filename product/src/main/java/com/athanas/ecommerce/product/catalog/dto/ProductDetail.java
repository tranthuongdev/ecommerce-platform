package com.athanas.ecommerce.product.catalog.dto;

import com.athanas.ecommerce.product.catalog.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductDetail(
        UUID id,
        UUID sellerId,
        String sku,
        String name,
        String description,
        BigDecimal basePrice,
        ProductStatus status,
        CategorySummary category,
        List<VariantDetail> variants,
        List<ImageDetail> images,
        Instant createdAt,
        Instant updatedAt
) {
    public record CategorySummary(UUID id, String name) {}
    public record VariantDetail(UUID id, String sku, String name, BigDecimal priceDelta, int stock) {}
    public record ImageDetail(UUID id, String url, int sortOrder, boolean isPrimary) {}
}
