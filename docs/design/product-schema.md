# Product Domain Schema Design (US-010)

**Date:** 2026-07-10
**Sprint:** 2, T6 Tuần 3
**Related:** US-010, US-011, US-012

## Goal

Thiết kế schema cho product catalog domain. Foundation cho:
- Sprint 2 US-011/012: public listing + admin CRUD
- Sprint 3: Cart sẽ ref product_variant_id
- Sprint 4-5: Order, Inventory sẽ ref product/variant

## Decisions

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Variant model | Separate product_variants table | Product có thể có nhiều biến thể (Red XL, Blue M). Single-row không scale. |
| 2 | Stock storage | INTEGER cột trên product_variants tạm thời | Sprint 5 sẽ refactor sang Inventory module với reservation pattern. Tránh over-engineer Sprint 2. |
| 3 | Price | base_price trên product, price_delta trên variant | Total = base + delta. Cho phép pricing nhanh + variant override. |
| 4 | Status enum | DRAFT/ACTIVE/INACTIVE/ARCHIVED (VARCHAR + CHECK) | Postgres ENUM type khó migrate. CHECK constraint linh hoạt hơn. |
| 5 | Category tree | parent_id self-FK (adjacency list) | Simple, đủ cho 2-3 level. Closure table overkill cho Sprint 2. |
| 6 | SKU uniqueness | UNIQUE INDEX global | Industry standard. SKU phải unique toàn shop. |
| 7 | Primary image | is_primary BOOLEAN + partial unique index | 1 product có max 1 primary image. Partial index enforce. |
| 8 | Seller FK | ON DELETE RESTRICT | Không cho xóa user còn product. Force admin xử lý trước. |
| 9 | Soft delete | product có deleted_at, variant/image hard delete cascade | Product là entity public-facing, cần audit. Variant/image phụ thuộc. |

## ERD (Mermaid)

\`\`\`mermaid
erDiagram
CATEGORIES {
UUID id PK
VARCHAR name UK
UUID parent_id FK "nullable, self-ref"
TIMESTAMP created_at
}

    PRODUCTS {
        UUID id PK
        UUID seller_id FK "→ users"
        UUID category_id FK "→ categories"
        VARCHAR sku UK
        VARCHAR name
        TEXT description
        NUMERIC base_price "12,2"
        VARCHAR status "DRAFT/ACTIVE/INACTIVE/ARCHIVED"
        TIMESTAMP deleted_at "nullable, soft delete"
        TIMESTAMP created_at
        UUID created_by
        TIMESTAMP updated_at
        UUID updated_by
        BIGINT version
    }

    PRODUCT_VARIANTS {
        UUID id PK
        UUID product_id FK "→ products"
        VARCHAR sku UK
        VARCHAR name "Red XL"
        NUMERIC price_delta "default 0"
        INTEGER stock "default 0, Sprint 5 refactor"
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT version
    }

    PRODUCT_IMAGES {
        UUID id PK
        UUID product_id FK "→ products"
        VARCHAR url
        INTEGER sort_order
        BOOLEAN is_primary
        TIMESTAMP created_at
    }

    USERS ||--o{ PRODUCTS : "sells"
    CATEGORIES ||--o{ CATEGORIES : "parent of"
    CATEGORIES ||--o{ PRODUCTS : "categorizes"
    PRODUCTS ||--o{ PRODUCT_VARIANTS : "has variants"
    PRODUCTS ||--o{ PRODUCT_IMAGES : "has images"
\`\`\`

## Indexes Plan

| Index | Table | Purpose |
|---|---|---|
| idx_products_category_status | products(category_id, status) WHERE deleted_at IS NULL | Public listing filter by category + status |
| idx_products_seller_created | products(seller_id, created_at DESC) | Seller dashboard "my products" |
| uq_products_sku | products(sku) | Global SKU uniqueness |
| idx_product_variants_product | product_variants(product_id) | FK reverse lookup |
| uq_product_variants_sku | product_variants(sku) | Variant SKU global unique |
| idx_product_images_product_primary | product_images(product_id) WHERE is_primary | Fetch primary image fast |
| uq_product_images_one_primary | product_images(product_id) WHERE is_primary | Max 1 primary per product (partial unique) |

## Constraints

- check_products_price_non_negative: base_price >= 0
- check_products_status_valid: status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED')
- check_variants_stock_non_negative: stock >= 0
- check_variants_price_delta_valid: base_price + price_delta >= 0 (defer to app layer — needs JOIN)
- FK products.seller_id → users(id) ON DELETE RESTRICT
- FK products.category_id → categories(id) ON DELETE RESTRICT
- FK product_variants.product_id → products(id) ON DELETE CASCADE
- FK product_images.product_id → products(id) ON DELETE CASCADE
- FK categories.parent_id → categories(id) ON DELETE SET NULL

## Seed Data (V3 migration)

3 root categories cho test:
- Electronics
- Fashion
- Books

## Out of Scope for Sprint 2

- Multi-currency (Sprint 4+ Order)
- Product reviews (post-MVP)
- Tag system (post-MVP)
- Inventory reservation pattern (Sprint 5)
- Search-optimized fields (Elasticsearch — Sprint 11 Q2)
- Variant attributes structured (color/size as separate cols — defer if needed)

## Verification Plan (cho US-010 implementation T2 Tuần 4)

1. Flyway V3 migration runs success
2. 3 categories seeded
3. Entities map to schema (Hibernate ddl-auto=validate passes)
4. Insert product with invalid status → CHECK constraint fails
5. Insert product with negative price → CHECK constraint fails
6. Insert 2 product_images with is_primary=true cùng product → unique constraint fails
7. Delete user còn product → RESTRICT, fails
8. Repository tests basic save + find