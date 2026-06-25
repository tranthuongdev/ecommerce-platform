package com.athanas.ecommerce.product.catalog.api;

import com.athanas.ecommerce.product.catalog.ProductService;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.ProductSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
@Validated
public class ProductPublicController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "basePrice", "name");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ProductService productService;

    @GetMapping
    public PageResponse<ProductSummary> listProducts(
            @RequestParam(required = false) UUID category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        Sort resolvedSort = SortParser.parseSort(sort, ALLOWED_SORT_FIELDS, DEFAULT_SORT);
        Pageable pageable = PageRequest.of(page, size, resolvedSort);
        return PageResponse.from(productService.listPublic(category, pageable));
    }

    @GetMapping("/{id}")
    public ProductDetail getProduct(@PathVariable UUID id) {
        return productService.getPublicById(id);
    }
}
