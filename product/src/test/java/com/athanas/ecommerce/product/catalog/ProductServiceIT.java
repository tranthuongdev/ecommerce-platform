package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.ProductSummary;
import com.athanas.ecommerce.product.catalog.dto.UpdateProductRequest;
import com.athanas.ecommerce.product.catalog.error.CategoryNotFoundException;
import com.athanas.ecommerce.product.catalog.error.ProductAccessDeniedException;
import com.athanas.ecommerce.product.catalog.error.SkuAlreadyExistsException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class ProductServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    private UUID sellerId1;
    private UUID sellerId2;
    private UUID electronicsId;
    private UUID fashionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM product_images");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM users");

        sellerId1 = UUID.randomUUID();
        sellerId2 = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','Seller1',true,0)",
                sellerId1, "s1-" + sellerId1 + "@t.com");
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','Seller2',true,0)",
                sellerId2, "s2-" + sellerId2 + "@t.com");

        electronicsId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Electronics'", UUID.class);
        fashionId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Fashion'", UUID.class);
    }

    private CreateProductRequest request(String sku, UUID categoryId, ProductStatus status, UUID sellerId) {
        return new CreateProductRequest(sku, "Product " + sku, null,
                new BigDecimal("10.00"), categoryId, status, sellerId);
    }

    @Test
    void shouldListOnlyActiveProductsPublic() {
        productService.create(request("SKU-DRAFT", electronicsId, ProductStatus.DRAFT, null), sellerId1, Set.of("SELLER"));
        productService.create(request("SKU-ACTIVE", electronicsId, ProductStatus.ACTIVE, null), sellerId1, Set.of("SELLER"));
        productService.create(request("SKU-ARCHIVED", electronicsId, ProductStatus.ARCHIVED, null), sellerId1, Set.of("SELLER"));

        Page<ProductSummary> result = productService.listPublic(null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).sku()).isEqualTo("SKU-ACTIVE");
    }

    @Test
    void shouldFilterByCategory() {
        productService.create(request("SKU-E1", electronicsId, ProductStatus.ACTIVE, null), sellerId1, Set.of("SELLER"));
        productService.create(request("SKU-E2", electronicsId, ProductStatus.ACTIVE, null), sellerId1, Set.of("SELLER"));
        productService.create(request("SKU-F1", fashionId, ProductStatus.ACTIVE, null), sellerId1, Set.of("SELLER"));

        Page<ProductSummary> electronics = productService.listPublic(electronicsId, PageRequest.of(0, 20));
        Page<ProductSummary> fashion = productService.listPublic(fashionId, PageRequest.of(0, 20));

        assertThat(electronics.getTotalElements()).isEqualTo(2);
        assertThat(fashion.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldCreateProductWithCallerAsSeller() {
        ProductDetail detail = productService.create(
                request("SKU-CREATE", electronicsId, null, null),
                sellerId1, Set.of("SELLER"));

        assertThat(detail.sellerId()).isEqualTo(sellerId1);
        assertThat(detail.status()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void shouldAllowAdminToSetSellerId() {
        CreateProductRequest req = new CreateProductRequest(
                "SKU-ADMIN", "Admin Product", null,
                new BigDecimal("10.00"), electronicsId, null, sellerId2);

        ProductDetail detail = productService.create(req, sellerId1, Set.of("ADMIN"));

        assertThat(detail.sellerId()).isEqualTo(sellerId2);
    }

    @Test
    void shouldRejectDuplicateSku() {
        productService.create(request("SKU-DUP", electronicsId, null, null), sellerId1, Set.of("SELLER"));

        assertThatThrownBy(() ->
                productService.create(request("SKU-DUP", electronicsId, null, null), sellerId1, Set.of("SELLER")))
                .isInstanceOf(SkuAlreadyExistsException.class);
    }

    @Test
    void shouldRejectUnknownCategory() {
        UUID fakeCategory = UUID.randomUUID();
        CreateProductRequest req = new CreateProductRequest(
                "SKU-CAT", "Cat Product", null, new BigDecimal("10.00"), fakeCategory, null, null);

        assertThatThrownBy(() -> productService.create(req, sellerId1, Set.of("SELLER")))
                .isInstanceOf(CategoryNotFoundException.class);
    }

    @Test
    void shouldAllowSellerToUpdateOwnProduct() {
        ProductDetail created = productService.create(
                request("SKU-UPD", electronicsId, null, null), sellerId1, Set.of("SELLER"));

        UpdateProductRequest updateReq = new UpdateProductRequest("Updated Name", null, null, null, null);
        ProductDetail updated = productService.update(created.id(), updateReq, sellerId1, Set.of("SELLER"));

        assertThat(updated.name()).isEqualTo("Updated Name");
    }

    @Test
    void shouldRejectSellerUpdatingOthersProduct() {
        ProductDetail created = productService.create(
                request("SKU-OWN", electronicsId, null, null), sellerId1, Set.of("SELLER"));

        UpdateProductRequest updateReq = new UpdateProductRequest("Hacked", null, null, null, null);

        assertThatThrownBy(() ->
                productService.update(created.id(), updateReq, sellerId2, Set.of("SELLER")))
                .isInstanceOf(ProductAccessDeniedException.class);
    }

    @Test
    void shouldAllowAdminToUpdateAnyProduct() {
        ProductDetail created = productService.create(
                request("SKU-ADMUPD", electronicsId, null, null), sellerId1, Set.of("SELLER"));

        UpdateProductRequest updateReq = new UpdateProductRequest("Admin Updated", null, null, null, null);
        UUID adminId = UUID.randomUUID();
        ProductDetail updated = productService.update(created.id(), updateReq, adminId, Set.of("ADMIN"));

        assertThat(updated.name()).isEqualTo("Admin Updated");
    }

    @Test
    void shouldSoftDeleteAndHideFromQuery() {
        ProductDetail created = productService.create(
                request("SKU-DEL", electronicsId, ProductStatus.ACTIVE, null), sellerId1, Set.of("SELLER"));

        productService.softDelete(created.id(), sellerId1, Set.of("SELLER"));

        Page<ProductSummary> result = productService.listPublic(null, PageRequest.of(0, 20));
        assertThat(result.getContent()).noneMatch(p -> p.id().equals(created.id()));
    }

    @Test
    void shouldVerifyNoN1QueryOnListPublic() {
        for (int i = 0; i < 10; i++) {
            productService.create(
                    request("SKU-N1-" + i, electronicsId, ProductStatus.ACTIVE, null),
                    sellerId1, Set.of("SELLER"));
        }

        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        Page<ProductSummary> page = productService.listPublic(null, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isGreaterThanOrEqualTo(10);
        assertThat(stats.getQueryExecutionCount())
                .as("Expected <= 4 queries (count + select + categories IN + images IN)")
                .isLessThanOrEqualTo(4);
    }
}
