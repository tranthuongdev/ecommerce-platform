package com.athanas.ecommerce.product.catalog.error;

import com.athanas.ecommerce.common.error.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
@RequiredArgsConstructor
public class ProductExceptionHandler {

    private final ProblemDetailFactory factory;

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleProductNotFound(ProductNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(HttpStatus.NOT_FOUND, ex.getMessage(),
                        "https://athanas.dev/errors/product-not-found"));
    }

    @ExceptionHandler(SkuAlreadyExistsException.class)
    public ResponseEntity<ProblemDetail> handleSkuExists(SkuAlreadyExistsException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(HttpStatus.CONFLICT, ex.getMessage(),
                        "https://athanas.dev/errors/sku-exists"));
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleCategoryNotFound(CategoryNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(HttpStatus.NOT_FOUND, ex.getMessage(),
                        "https://athanas.dev/errors/category-not-found"));
    }

    @ExceptionHandler(ProductAccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleProductAccessDenied(ProductAccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(factory.of(HttpStatus.FORBIDDEN, ex.getMessage(),
                        "https://athanas.dev/errors/product-access-denied"));
    }
}
