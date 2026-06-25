package com.athanas.ecommerce.product.catalog.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SortParserTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "basePrice", "name");
    private static final Sort DEFAULT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void shouldParseValidSort() {
        Sort sort = SortParser.parseSort("name,asc", ALLOWED, DEFAULT);
        Sort.Order order = sort.getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void shouldFallbackOnInvalidField() {
        Sort sort = SortParser.parseSort("password,asc", ALLOWED, DEFAULT);
        assertThat(sort).isEqualTo(DEFAULT);
    }

    @Test
    void shouldFallbackOnBlankInput() {
        assertThat(SortParser.parseSort("", ALLOWED, DEFAULT)).isEqualTo(DEFAULT);
        assertThat(SortParser.parseSort("   ", ALLOWED, DEFAULT)).isEqualTo(DEFAULT);
        assertThat(SortParser.parseSort(null, ALLOWED, DEFAULT)).isEqualTo(DEFAULT);
    }

    @Test
    void shouldDefaultDirectionToDescWhenOmitted() {
        Sort sort = SortParser.parseSort("name", ALLOWED, DEFAULT);
        Sort.Order order = sort.getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void shouldIgnoreCaseOfDirection() {
        Sort sort = SortParser.parseSort("name,ASC", ALLOWED, DEFAULT);
        Sort.Order order = sort.getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
