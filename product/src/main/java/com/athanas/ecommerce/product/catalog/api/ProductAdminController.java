package com.athanas.ecommerce.product.catalog.api;

import com.athanas.ecommerce.common.security.PrincipalView;
import com.athanas.ecommerce.product.catalog.ProductService;
import com.athanas.ecommerce.product.catalog.dto.CreateProductRequest;
import com.athanas.ecommerce.product.catalog.dto.ProductDetail;
import com.athanas.ecommerce.product.catalog.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/products")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ProductDetail create(@Valid @RequestBody CreateProductRequest req,
                                @AuthenticationPrincipal PrincipalView principal) {
        return productService.create(req, principal.userId(), principal.roleNames());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ProductDetail get(@PathVariable UUID id,
                             @AuthenticationPrincipal PrincipalView principal) {
        return productService.getForOwnerOrAdmin(id, principal.userId(), principal.roleNames());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public ProductDetail update(@PathVariable UUID id,
                                @Valid @RequestBody UpdateProductRequest req,
                                @AuthenticationPrincipal PrincipalView principal) {
        return productService.update(id, req, principal.userId(), principal.roleNames());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    public void softDelete(@PathVariable UUID id,
                           @AuthenticationPrincipal PrincipalView principal) {
        productService.softDelete(id, principal.userId(), principal.roleNames());
    }
}
