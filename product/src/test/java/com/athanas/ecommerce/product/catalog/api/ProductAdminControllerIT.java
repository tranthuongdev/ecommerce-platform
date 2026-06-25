package com.athanas.ecommerce.product.catalog.api;

import com.athanas.ecommerce.product.catalog.ProductService;
import com.athanas.ecommerce.product.catalog.ProductStatus;
import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.testsupport.TestPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductAdminControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;
    @Autowired ProductService productService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;

    private UUID sellerId1;
    private UUID sellerId2;
    private UUID adminId;
    private UUID electronicsId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM product_images");
        jdbcTemplate.update("DELETE FROM product_variants");
        jdbcTemplate.update("DELETE FROM products");
        jdbcTemplate.update("DELETE FROM users");

        sellerId1 = UUID.randomUUID();
        sellerId2 = UUID.randomUUID();
        adminId = UUID.randomUUID();

        jdbcTemplate.update("INSERT INTO users(id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','S1',true,0)",
                sellerId1, "s1-" + sellerId1 + "@t.com");
        jdbcTemplate.update("INSERT INTO users(id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','S2',true,0)",
                sellerId2, "s2-" + sellerId2 + "@t.com");
        jdbcTemplate.update("INSERT INTO users(id,email,password_hash,full_name,enabled,version) VALUES(?,?,'h','Admin',true,0)",
                adminId, "admin-" + adminId + "@t.com");

        electronicsId = jdbcTemplate.queryForObject("SELECT id FROM categories WHERE name='Electronics'", UUID.class);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor asRole(UUID userId, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(
                new TestPrincipal(userId, Set.of(role)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        ));
    }

    private String createProductJson(String sku) throws Exception {
        return objectMapper.writeValueAsString(new CreateProductRequest(
                sku, "Product " + sku, null, new BigDecimal("49.99"), electronicsId, null, null));
    }

    @Test
    void shouldCreateProductAsSeller() throws Exception {
        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(sellerId1, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("SKU-S1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-S1"))
                .andExpect(jsonPath("$.sellerId").value(sellerId1.toString()));
    }

    @Test
    void shouldCreateProductAsAdmin_implicitSellerId() throws Exception {
        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("SKU-ADM")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(adminId.toString()));
    }

    @Test
    void shouldCreateProductAsAdmin_explicitSellerId() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateProductRequest(
                "SKU-ADM2", "Admin With Seller", null, new BigDecimal("10.00"),
                electronicsId, null, sellerId1));

        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(sellerId1.toString()));
    }

    @Test
    void shouldRejectCreateAsUser() throws Exception {
        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(UUID.randomUUID(), "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("SKU-USR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectCreateWithoutAuth() throws Exception {
        mockMvc.perform(post("/v1/admin/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("SKU-NOAUTH")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDuplicateSku() throws Exception {
        productService.create(new CreateProductRequest("SKU-DUP", "Dup", null,
                new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(sellerId1, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createProductJson("SKU-DUP")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/sku-exists"));
    }

    @Test
    void shouldRejectInvalidCategory() throws Exception {
        String body = objectMapper.writeValueAsString(new CreateProductRequest(
                "SKU-CAT", "Cat", null, new BigDecimal("10.00"), UUID.randomUUID(), null, null));

        mockMvc.perform(post("/v1/admin/products")
                        .with(asRole(sellerId1, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/category-not-found"));
    }

    @Test
    void shouldAllowSellerUpdateOwnProduct() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-UPD", "Old",
                null, new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        String body = "{\"name\":\"Updated Name\"}";
        mockMvc.perform(put("/v1/admin/products/" + created.id())
                        .with(asRole(sellerId1, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    void shouldRejectSellerUpdateOthersProduct() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-OWN", "Own",
                null, new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        mockMvc.perform(put("/v1/admin/products/" + created.id())
                        .with(asRole(sellerId2, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Hacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://athanas.dev/errors/product-access-denied"));
    }

    @Test
    void shouldAllowAdminUpdateAnyProduct() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-ADMPD", "Old",
                null, new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        mockMvc.perform(put("/v1/admin/products/" + created.id())
                        .with(asRole(adminId, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturn404OnUpdateNonExistent() throws Exception {
        mockMvc.perform(put("/v1/admin/products/" + UUID.randomUUID())
                        .with(asRole(sellerId1, "SELLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldSoftDeleteAsSellerOwner() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-DEL", "Delete Me",
                null, new BigDecimal("10.00"), electronicsId, ProductStatus.ACTIVE, null),
                sellerId1, Set.of("SELLER"));

        mockMvc.perform(delete("/v1/admin/products/" + created.id())
                        .with(asRole(sellerId1, "SELLER")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/v1/products/" + created.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectSoftDeleteByOtherSeller() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-DELOWN", "Own",
                null, new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        mockMvc.perform(delete("/v1/admin/products/" + created.id())
                        .with(asRole(sellerId2, "SELLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldGetOwnDraftProduct() throws Exception {
        ProductDetail draft = productService.create(new CreateProductRequest("SKU-DRAFT", "Draft",
                null, new BigDecimal("5.00"), electronicsId, ProductStatus.DRAFT, null),
                sellerId1, Set.of("SELLER"));

        mockMvc.perform(get("/v1/admin/products/" + draft.id())
                        .with(asRole(sellerId1, "SELLER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/v1/products/" + draft.id()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldNotLeakOtherSellersProductOnAdminGet() throws Exception {
        ProductDetail created = productService.create(new CreateProductRequest("SKU-LEAK", "Leak",
                null, new BigDecimal("10.00"), electronicsId, null, null), sellerId1, Set.of("SELLER"));

        mockMvc.perform(get("/v1/admin/products/" + created.id())
                        .with(asRole(sellerId2, "SELLER")))
                .andExpect(status().isForbidden());
    }
}
