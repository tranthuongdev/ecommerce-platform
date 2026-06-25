package com.athanas.ecommerce.product.catalog.dto;

import com.athanas.ecommerce.product.catalog.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10_000) String description,
        @NotNull @DecimalMin("0.0") BigDecimal basePrice,
        @NotNull UUID categoryId,
        ProductStatus status,
        UUID sellerId
) {}
