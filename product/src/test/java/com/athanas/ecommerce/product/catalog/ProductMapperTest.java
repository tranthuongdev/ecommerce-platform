package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.ProductSummary;
import com.athanas.ecommerce.product.category.Category;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    private final ProductMapper mapper = new ProductMapper();

    private Product buildProduct() {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setSellerId(UUID.randomUUID());
        p.setCategoryId(UUID.randomUUID());
        p.setSku("SKU-TEST");
        p.setName("Test Product");
        p.setDescription("A description");
        p.setBasePrice(new BigDecimal("49.99"));
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    private Category buildCategory(UUID id) {
        Category c = new Category();
        c.setId(id);
        c.setName("Electronics");
        return c;
    }

    @Test
    void shouldMapEntityToSummary() {
        Product p = buildProduct();
        String categoryName = "Electronics";
        String imageUrl = "https://cdn.example.com/img.jpg";

        ProductSummary summary = mapper.toSummary(p, categoryName, imageUrl);

        assertThat(summary.id()).isEqualTo(p.getId());
        assertThat(summary.sku()).isEqualTo("SKU-TEST");
        assertThat(summary.name()).isEqualTo("Test Product");
        assertThat(summary.basePrice()).isEqualByComparingTo("49.99");
        assertThat(summary.status()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(summary.categoryName()).isEqualTo(categoryName);
        assertThat(summary.primaryImageUrl()).isEqualTo(imageUrl);
    }

    @Test
    void shouldMapEntityToDetailIncludingVariants() {
        Product p = buildProduct();
        Category c = buildCategory(p.getCategoryId());

        ProductVariant v = new ProductVariant();
        v.setId(UUID.randomUUID());
        v.setProduct(p);
        v.setSku("VAR-001");
        v.setName("Red XL");
        v.setPriceDelta(new BigDecimal("5.00"));
        v.setStock(10);
        p.getVariants().add(v);

        ProductDetail detail = mapper.toDetail(p, c);

        assertThat(detail.id()).isEqualTo(p.getId());
        assertThat(detail.description()).isEqualTo("A description");
        assertThat(detail.category().name()).isEqualTo("Electronics");
        assertThat(detail.variants()).hasSize(1);
        assertThat(detail.variants().get(0).sku()).isEqualTo("VAR-001");
        assertThat(detail.variants().get(0).stock()).isEqualTo(10);
        assertThat(detail.images()).isEmpty();
    }

    @Test
    void shouldPassNullDescriptionThrough() {
        Product p = buildProduct();
        p.setDescription(null);
        Category c = buildCategory(p.getCategoryId());

        ProductDetail detail = mapper.toDetail(p, c);

        assertThat(detail.description()).isNull();
    }

    @Test
    void shouldDefaultStatusToDraftWhenNullInRequest() {
        CreateProductRequest req = new CreateProductRequest(
                "SKU-NEW", "New Product", null,
                new BigDecimal("19.99"), UUID.randomUUID(),
                null, null
        );

        Product entity = mapper.toEntity(req, UUID.randomUUID());

        assertThat(entity.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void shouldUseExplicitStatusFromRequest() {
        CreateProductRequest req = new CreateProductRequest(
                "SKU-ACTIVE", "Active Product", null,
                new BigDecimal("29.99"), UUID.randomUUID(),
                ProductStatus.ACTIVE, null
        );

        Product entity = mapper.toEntity(req, UUID.randomUUID());

        assertThat(entity.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }
}
