package com.athanas.ecommerce.product.catalog.api;

import org.springframework.data.domain.Sort;

import java.util.Set;

public final class SortParser {

    private SortParser() {}

    public static Sort parseSort(String raw, Set<String> allowedFields, Sort defaultSort) {
        if (raw == null || raw.isBlank()) return defaultSort;
        String[] parts = raw.split(",", 2);
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) return defaultSort;
        Sort.Direction direction = Sort.Direction.DESC;
        if (parts.length > 1) {
            try {
                direction = Sort.Direction.fromString(parts[1].trim());
            } catch (IllegalArgumentException e) {
                direction = Sort.Direction.DESC;
            }
        }
        return Sort.by(direction, field);
    }
}
