package com.athanas.ecommerce.product.catalog.error;

import java.util.UUID;

public class ProductAccessDeniedException extends RuntimeException {

    public ProductAccessDeniedException(UUID productId, UUID callerId) {
        super("User " + callerId + " cannot modify product " + productId);
    }
}
