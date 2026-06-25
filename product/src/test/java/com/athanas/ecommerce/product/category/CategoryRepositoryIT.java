package com.athanas.ecommerce.product.category;

import com.athanas.ecommerce.common.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class CategoryRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired CategoryRepository categoryRepository;

    @Test
    void shouldSeedThreeRootCategories() {
        assertThat(categoryRepository.findByName("Electronics")).isPresent();
        assertThat(categoryRepository.findByName("Fashion")).isPresent();
        assertThat(categoryRepository.findByName("Books")).isPresent();
    }

    @Test
    void shouldSupportSelfReferentialParent() {
        Category electronics = categoryRepository.findByName("Electronics").orElseThrow();

        Category laptops = new Category();
        laptops.setName("Laptops");
        laptops.setParentId(electronics.getId());
        categoryRepository.saveAndFlush(laptops);

        List<Category> children = categoryRepository.findByParentId(electronics.getId());
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getName()).isEqualTo("Laptops");
    }

    @Test
    void shouldRejectDuplicateName() {
        Category dup = new Category();
        dup.setName("Electronics");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> categoryRepository.saveAndFlush(dup))
                .isInstanceOf(Exception.class);
    }
}
