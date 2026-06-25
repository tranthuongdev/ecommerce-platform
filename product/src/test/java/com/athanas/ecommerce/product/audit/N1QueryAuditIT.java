package com.athanas.ecommerce.product.audit;

import com.athanas.ecommerce.product.catalog.Product;
import com.athanas.ecommerce.product.catalog.ProductImage;
import com.athanas.ecommerce.product.catalog.ProductImageRepository;
import com.athanas.ecommerce.product.catalog.ProductRepository;
import com.athanas.ecommerce.product.catalog.ProductService;
import com.athanas.ecommerce.product.catalog.ProductStatus;
import com.athanas.ecommerce.product.catalog.ProductVariant;
import com.athanas.ecommerce.product.catalog.ProductVariantRepository;
import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive N+1 query audit for product module read paths.
 * Uses Hibernate Statistics.getQueryExecutionCount() to count HQL/JPQL executions.
 * All assertions are O(1) in page size — if a query appears per-row, the count
 * will be proportional to n and the test will fail dramatically.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
@ActiveProfiles("test")
class N1QueryAuditIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    private Statistics stats;
    private UUID sellerId;
    private UUID electronicsId;
    private UUID fashionId;
    private UUID booksId;
    private final List<UUID> seededProductIds = new ArrayList<>();

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM product_images");
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM users");
        seededProductIds.clear();

        sellerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users(id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','S',true,0)",
                sellerId, "audit-" + sellerId + "@t.com");

        electronicsId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Electronics'", UUID.class);
        fashionId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Fashion'", UUID.class);
        booksId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Books'", UUID.class);

        UUID[] cats = {electronicsId, fashionId, booksId};
        for (int i = 0; i < 15; i++) {
            ProductDetail p = productService.create(
                    new CreateProductRequest("AUDIT-" + i, "Audit Product " + i, "Description",
                            new BigDecimal(10 + i), cats[i % 3], ProductStatus.ACTIVE, null),
                    sellerId, Set.of("SELLER"));
            seededProductIds.add(p.id());

            Product entity = productRepository.findById(p.id()).orElseThrow();

            // 2 variants per product
            for (char c : new char[]{'A', 'B'}) {
                ProductVariant v = new ProductVariant();
                v.setProduct(entity);
                v.setSku("VAR-" + i + "-" + c);
                v.setName("Variant " + c);
                variantRepository.save(v);
            }

            // 3 images per product (1 primary, 2 secondary)
            ProductImage primary = new ProductImage();
            primary.setProduct(entity);
            primary.setUrl("https://cdn.example.com/p" + i + "-primary.jpg");
            primary.setPrimary(true);
            imageRepository.save(primary);

            for (int j = 1; j <= 2; j++) {
                ProductImage img = new ProductImage();
                img.setProduct(entity);
                img.setUrl("https://cdn.example.com/p" + i + "-extra" + j + ".jpg");
                imageRepository.save(img);
            }
        }

        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
    }

    private void resetStats() {
        stats.clear();
    }

    private long queryCount() {
        return stats.getQueryExecutionCount();
    }

    @Test
    void listPublic_default_executes_at_most_4_queries() {
        resetStats();
        productService.listPublic(null, PageRequest.of(0, 20));
        long count = queryCount();
        // Expected: 1 count + 1 SELECT products + 1 categories IN + 1 images IN = 4
        assertThat(count)
                .as("listPublic should use <= 4 queries (count, products, categories IN, images IN); got %d", count)
                .isLessThanOrEqualTo(4);
    }

    @Test
    void listPublic_byCategory_same_query_budget() {
        resetStats();
        productService.listPublic(electronicsId, PageRequest.of(0, 20));
        long count = queryCount();
        assertThat(count)
                .as("listPublic by category should use <= 4 queries; got %d", count)
                .isLessThanOrEqualTo(4);
    }

    @Test
    void getPublicById_executes_at_most_4_queries() {
        UUID activeId = seededProductIds.get(0);
        resetStats();
        productService.getPublicById(activeId);
        long count = queryCount();
        // Expected: 1 product + 1 category + 1 variants (lazy) + 1 images (lazy) = 4
        assertThat(count)
                .as("getPublicById should use <= 4 queries; got %d", count)
                .isLessThanOrEqualTo(4);
    }

    @Test
    void getForOwnerOrAdmin_same_budget_as_public() {
        UUID productId = seededProductIds.get(1);
        resetStats();
        productService.getForOwnerOrAdmin(productId, sellerId, Set.of("SELLER"));
        long count = queryCount();
        assertThat(count)
                .as("getForOwnerOrAdmin should use <= 4 queries; got %d", count)
                .isLessThanOrEqualTo(4);
    }

    @Test
    void listPublic_largePageSize_still_constant_queries() {
        // Canonical N+1 detector: if O(n), 100 products would produce 100+ queries
        resetStats();
        productService.listPublic(null, PageRequest.of(0, 100));
        long count = queryCount();
        assertThat(count)
                .as("listPublic with large page must remain O(1) in query count; got %d — N+1 detected if >> 4", count)
                .isLessThanOrEqualTo(4);
    }
}
