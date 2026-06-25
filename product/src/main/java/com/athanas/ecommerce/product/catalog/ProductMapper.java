package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.ProductSummary;
import com.athanas.ecommerce.product.category.Category;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ProductMapper {

    public ProductSummary toSummary(Product p, String categoryName, String primaryImageUrl) {
        return new ProductSummary(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getBasePrice(),
                p.getStatus(),
                p.getCategoryId(),
                categoryName,
                primaryImageUrl
        );
    }

    public ProductDetail toDetail(Product p, Category c) {
        List<ProductDetail.VariantDetail> variants = p.getVariants().stream()
                .map(this::fromVariant)
                .toList();

        List<ProductDetail.ImageDetail> images = p.getImages().stream()
                .map(this::fromImage)
                .toList();

        return new ProductDetail(
                p.getId(),
                p.getSellerId(),
                p.getSku(),
                p.getName(),
                p.getDescription(),
                p.getBasePrice(),
                p.getStatus(),
                new ProductDetail.CategorySummary(c.getId(), c.getName()),
                variants,
                images,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }

    ProductDetail.VariantDetail fromVariant(ProductVariant v) {
        return new ProductDetail.VariantDetail(
                v.getId(),
                v.getSku(),
                v.getName(),
                v.getPriceDelta(),
                v.getStock()
        );
    }

    ProductDetail.ImageDetail fromImage(ProductImage img) {
        return new ProductDetail.ImageDetail(
                img.getId(),
                img.getUrl(),
                img.getSortOrder(),
                img.isPrimary()
        );
    }

    public Product toEntity(CreateProductRequest req, UUID sellerId) {
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setCategoryId(req.categoryId());
        p.setSku(req.sku());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setBasePrice(req.basePrice());
        p.setStatus(req.status() != null ? req.status() : ProductStatus.DRAFT);
        return p;
    }
}
