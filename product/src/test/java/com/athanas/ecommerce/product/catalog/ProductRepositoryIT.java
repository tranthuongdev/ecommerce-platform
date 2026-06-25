package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.common.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ProductRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ProductRepository productRepository;
    @Autowired ProductVariantRepository variantRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @PersistenceContext EntityManager entityManager;

    private UUID sellerId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        sellerId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, full_name, enabled, version)
                VALUES (?, ?, 'hash', 'Test Seller', true, 0)
                """, sellerId, "seller-" + sellerId + "@test.com");

        categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE name = 'Electronics'", UUID.class);
    }

    private Product buildProduct(String sku) {
        Product p = new Product();
        p.setSellerId(sellerId);
        p.setCategoryId(categoryId);
        p.setSku(sku);
        p.setName("Test Product " + sku);
        p.setBasePrice(new BigDecimal("99.99"));
        p.setStatus(ProductStatus.DRAFT);
        return p;
    }

    @Test
    void shouldSaveProductWithDefaults() {
        Product saved = productRepository.saveAndFlush(buildProduct("SKU-001"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.DRAFT);
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldRejectNegativePrice() {
        Product p = buildProduct("SKU-002");
        p.setBasePrice(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(p))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldRejectDuplicateSku() {
        productRepository.saveAndFlush(buildProduct("SKU-DUP"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(buildProduct("SKU-DUP")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldSoftDeleteProduct() {
        Product p = productRepository.saveAndFlush(buildProduct("SKU-SOFT"));
        UUID id = p.getId();

        jdbcTemplate.update("UPDATE products SET deleted_at = NOW() WHERE id = ?", id);
        entityManager.clear();

        assertThat(productRepository.findById(id)).isEmpty();
    }

    @Test
    void shouldEnforceSellerFkRestrict() {
        productRepository.saveAndFlush(buildProduct("SKU-FK"));

        assertThatThrownBy(() ->
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", sellerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldCascadeDeleteVariants() {
        Product p = productRepository.saveAndFlush(buildProduct("SKU-CASCADE"));

        ProductVariant v = new ProductVariant();
        v.setProduct(p);
        v.setSku("VAR-001");
        v.setName("Variant 1");
        p.getVariants().add(v);
        productRepository.saveAndFlush(p);

        UUID variantId = p.getVariants().get(0).getId();
        assertThat(variantRepository.findById(variantId)).isPresent();

        jdbcTemplate.update("DELETE FROM products WHERE id = ?", p.getId());
        entityManager.clear();

        assertThat(variantRepository.findById(variantId)).isEmpty();
    }

    @Test
    void shouldFindBySku() {
        productRepository.saveAndFlush(buildProduct("SKU-FIND"));

        assertThat(productRepository.findBySku("SKU-FIND")).isPresent();
        assertThat(productRepository.existsBySku("SKU-FIND")).isTrue();
        assertThat(productRepository.existsBySku("NOPE")).isFalse();
    }
}
