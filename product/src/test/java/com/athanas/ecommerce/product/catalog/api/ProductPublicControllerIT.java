package com.athanas.ecommerce.product.catalog.api;

import com.athanas.ecommerce.product.catalog.ProductImage;
import com.athanas.ecommerce.product.catalog.ProductImageRepository;
import com.athanas.ecommerce.product.catalog.ProductRepository;
import com.athanas.ecommerce.product.catalog.ProductService;
import com.athanas.ecommerce.product.catalog.ProductStatus;
import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.error.ProductNotFoundException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductPublicControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ProductService productService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductImageRepository imageRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManagerFactory entityManagerFactory;

    private UUID sellerId;
    private UUID electronicsId;
    private UUID fashionId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_images");
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM users");

        sellerId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','S',true,0)",
                sellerId, "s-" + sellerId + "@t.com");

        electronicsId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Electronics'", UUID.class);
        fashionId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Fashion'", UUID.class);

        // 10 ACTIVE electronics
        for (int i = 0; i < 10; i++) {
            ProductDetail p = productService.create(
                    new CreateProductRequest("ELEC-" + i, "Electronic " + i, null,
                            new BigDecimal(10 + i), electronicsId, ProductStatus.ACTIVE, null),
                    sellerId, Set.of("SELLER"));
            // Add primary image to first 5
            if (i < 5) {
                ProductImage img = new ProductImage();
                img.setProduct(productRepository.findById(p.id()).orElseThrow());
                img.setUrl("https://cdn.example.com/elec-" + i + ".jpg");
                img.setPrimary(true);
                imageRepository.save(img);
            }
        }
        // 3 DRAFT electronics
        for (int i = 0; i < 3; i++) {
            productService.create(
                    new CreateProductRequest("DRAFT-" + i, "Draft " + i, null,
                            new BigDecimal("5.00"), electronicsId, ProductStatus.DRAFT, null),
                    sellerId, Set.of("SELLER"));
        }
        // 2 ACTIVE fashion
        for (int i = 0; i < 2; i++) {
            productService.create(
                    new CreateProductRequest("FASH-" + i, "Fashion " + i, null,
                            new BigDecimal("20.00"), fashionId, ProductStatus.ACTIVE, null),
                    sellerId, Set.of("SELLER"));
        }
    }

    @Test
    void shouldReturnEmptyListWhenNoProducts() throws Exception {
        jdbcTemplate.update("DELETE FROM product_images");
        jdbcTemplate.update("DELETE FROM products");

        mockMvc.perform(get("/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void shouldReturnOnlyActiveProducts() throws Exception {
        mockMvc.perform(get("/v1/products?size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12));
    }

    @Test
    void shouldFilterByCategory() throws Exception {
        mockMvc.perform(get("/v1/products?category=" + electronicsId + "&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(10));

        mockMvc.perform(get("/v1/products?category=" + fashionId + "&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void shouldPaginate() throws Exception {
        mockMvc.perform(get("/v1/products?page=0&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/v1/products?page=2&size=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void shouldRejectInvalidPageParams() throws Exception {
        mockMvc.perform(get("/v1/products?size=200"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/products?size=0"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/products?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldSortByPriceAsc() throws Exception {
        mockMvc.perform(get("/v1/products?sort=basePrice,asc&size=100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].basePrice").value(10.0));
    }

    @Test
    void shouldFallbackToDefaultSortOnUnknownField() throws Exception {
        mockMvc.perform(get("/v1/products?sort=password,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12));
    }

    @Test
    void shouldReturnProductDetailForActiveProduct() throws Exception {
        ProductDetail activeProduct = productService.create(
                new CreateProductRequest("DETAIL-SKU", "Detail Product", "desc",
                        new BigDecimal("99.00"), electronicsId, ProductStatus.ACTIVE, null),
                sellerId, Set.of("SELLER"));

        mockMvc.perform(get("/v1/products/" + activeProduct.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activeProduct.id().toString()))
                .andExpect(jsonPath("$.sku").value("DETAIL-SKU"))
                .andExpect(jsonPath("$.description").value("desc"))
                .andExpect(jsonPath("$.category.name").value("Electronics"))
                .andExpect(jsonPath("$.variants").isArray())
                .andExpect(jsonPath("$.images").isArray());
    }

    @Test
    void shouldReturn404ForDraftProduct() throws Exception {
        ProductDetail draft = productService.create(
                new CreateProductRequest("DRAFT-DETAIL", "Draft Detail", null,
                        new BigDecimal("5.00"), electronicsId, ProductStatus.DRAFT, null),
                sellerId, Set.of("SELLER"));

        mockMvc.perform(get("/v1/products/" + draft.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/product-not-found"));
    }

    @Test
    void shouldReturn404ForNonExistentId() throws Exception {
        mockMvc.perform(get("/v1/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/product-not-found"));
    }

    @Test
    void shouldNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/products"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldNotExposeN1Query() throws Exception {
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        mockMvc.perform(get("/v1/products?size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(12));

        assertThat(stats.getQueryExecutionCount())
                .as("Expected <= 4 queries: count + products + categories IN + images IN")
                .isLessThanOrEqualTo(4);
    }
}
