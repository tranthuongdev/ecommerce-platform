package com.athanas.ecommerce.product.catalog;

import com.athanas.ecommerce.common.config.JpaAuditingConfig;
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
class ProductImageConstraintIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    private Product product;

    @BeforeEach
    void setUp() {
        UUID sellerId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, full_name, enabled, version)
                VALUES (?, ?, 'hash', 'Test Seller', true, 0)
                """, sellerId, "img-seller-" + sellerId + "@test.com");

        UUID categoryId = jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE name = 'Books'", UUID.class);

        product = new Product();
        product.setSellerId(sellerId);
        product.setCategoryId(categoryId);
        product.setSku("IMG-SKU-" + UUID.randomUUID().toString().substring(0, 8));
        product.setName("Image Test Product");
        product.setBasePrice(BigDecimal.TEN);
        product.setStatus(ProductStatus.ACTIVE);
        productRepository.saveAndFlush(product);
    }

    private ProductImage buildImage(boolean primary, String url) {
        ProductImage img = new ProductImage();
        img.setProduct(product);
        img.setUrl(url);
        img.setPrimary(primary);
        return img;
    }

    @Test
    void shouldAllowOnePrimaryImage() {
        imageRepository.saveAndFlush(buildImage(true, "https://cdn.example.com/primary.jpg"));

        assertThat(imageRepository.findByProductId(product.getId())).hasSize(1);
    }

    @Test
    void shouldRejectTwoPrimaryImagesForSameProduct() {
        imageRepository.saveAndFlush(buildImage(true, "https://cdn.example.com/img1.jpg"));

        assertThatThrownBy(() ->
                imageRepository.saveAndFlush(buildImage(true, "https://cdn.example.com/img2.jpg")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void shouldAllowMultipleNonPrimaryImages() {
        imageRepository.saveAndFlush(buildImage(false, "https://cdn.example.com/a.jpg"));
        imageRepository.saveAndFlush(buildImage(false, "https://cdn.example.com/b.jpg"));
        imageRepository.saveAndFlush(buildImage(false, "https://cdn.example.com/c.jpg"));

        assertThat(imageRepository.findByProductId(product.getId())).hasSize(3);
    }
}
