package com.athanas.ecommerce.product.catalog.dto;

import com.athanas.ecommerce.product.catalog.ProductStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSummary(
        UUID id,
        String sku,
        String name,
        BigDecimal basePrice,
        ProductStatus status,
        UUID categoryId,
        String categoryName,
        String primaryImageUrl
) {}
