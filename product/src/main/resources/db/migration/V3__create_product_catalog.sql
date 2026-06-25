-- =============================================================================
-- V3__create_product_catalog.sql
-- Sprint 2, US-010: Product catalog domain
-- =============================================================================

CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL UNIQUE,
    parent_id   UUID NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    CONSTRAINT chk_categories_name_length
        CHECK (length(trim(name)) >= 1)
);

CREATE INDEX idx_categories_parent ON categories (parent_id);

INSERT INTO categories (name) VALUES
    ('Electronics'),
    ('Fashion'),
    ('Books');

-- ---------------------------------------------------------------------------
CREATE TABLE products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    seller_id       UUID NOT NULL,
    category_id     UUID NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    base_price      NUMERIC(12,2) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    deleted_at      TIMESTAMP NULL,

    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_products_seller
        FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,
    CONSTRAINT chk_products_price_non_negative
        CHECK (base_price >= 0),
    CONSTRAINT chk_products_status_valid
        CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_products_name_length
        CHECK (length(trim(name)) >= 1),
    CONSTRAINT chk_products_sku_length
        CHECK (length(trim(sku)) >= 1)
);

CREATE UNIQUE INDEX uq_products_sku ON products (sku);
CREATE INDEX idx_products_category_status ON products (category_id, status)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_products_seller_created ON products (seller_id, created_at DESC);

-- ---------------------------------------------------------------------------
CREATE TABLE product_variants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL,
    sku             VARCHAR(64) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    price_delta     NUMERIC(12,2) NOT NULL DEFAULT 0,
    stock           INTEGER NOT NULL DEFAULT 0,

    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    created_by      UUID,
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_by      UUID,
    version         BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT fk_variants_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_variants_stock_non_negative
        CHECK (stock >= 0),
    CONSTRAINT chk_variants_name_length
        CHECK (length(trim(name)) >= 1)
);

CREATE UNIQUE INDEX uq_product_variants_sku ON product_variants (sku);
CREATE INDEX idx_product_variants_product ON product_variants (product_id);

-- ---------------------------------------------------------------------------
CREATE TABLE product_images (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      UUID NOT NULL,
    url             VARCHAR(500) NOT NULL,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    is_primary      BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT fk_images_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    CONSTRAINT chk_images_url_length
        CHECK (length(trim(url)) >= 1)
);

CREATE INDEX idx_product_images_product ON product_images (product_id);
CREATE UNIQUE INDEX uq_product_images_one_primary ON product_images (product_id)
    WHERE is_primary = true;
