package com.athanas.ecommerce.product.catalog.error;

public class SkuAlreadyExistsException extends RuntimeException {

    public SkuAlreadyExistsException(String sku) {
        super("SKU already exists: " + sku);
    }
}
