package com.athanas.ecommerce.product.catalog.dto;

import com.athanas.ecommerce.product.catalog.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateProductRequest(
        @Size(max = 200) String name,
        @Size(max = 10_000) String description,
        @DecimalMin("0.0") BigDecimal basePrice,
        UUID categoryId,
        ProductStatus status
) {}
