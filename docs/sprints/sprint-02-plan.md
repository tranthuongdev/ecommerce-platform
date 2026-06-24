# Sprint 2 — Auth Hardening + Product Schema

**Duration:** Tuần 3-4 (Sprint 2 of 24)
**Sprint Goal:** Auth có RBAC enforcement + logout + rate limit production-grade. Product module có schema + public listing + admin CRUD với ownership check. Coverage gate 80% trên auth. Zero N+1 query verified.

---

## User Stories Committed

### US-007: RBAC Enforcement on Protected Endpoints

> As the system, I want to enforce role-based access on endpoints via JWT claims, so that USER/SELLER/ADMIN have appropriate access levels.

**Acceptance Criteria:**
- JwtAuthenticationFilter parse access token từ Authorization header
- Set SecurityContext với UserDetails có authorities = roles từ JWT
- Test endpoint `GET /v1/test/user-only`, `GET /v1/test/seller-only`, `GET /v1/test/admin-only` cho test (sẽ remove cuối sprint)
- 401 nếu thiếu/sai token, 403 nếu sai role
- @PreAuthorize("hasRole('ADMIN')") syntax (Spring Security 6)

**DoD:**
- Integration test matrix: 3 roles × 3 endpoints + 2 negative (no token, expired token)
- JwtAuthenticationFilter có unit test

---

### US-008: Logout + Token Revocation

> As a user, I want to logout so that my tokens are immediately invalidated even before TTL expires.

**Acceptance Criteria:**
- `POST /v1/auth/logout` requires valid access token (Bearer auth)
- Revoke current refresh token (set revoked_at trong DB)
- Add access token JTI vào Redis blacklist với TTL = remaining access TTL
- JwtAuthenticationFilter check blacklist → 401 nếu JTI bị revoke
- JWT generator phải include `jti` claim (UUID per token)

**DoD:**
- Integration test: login → logout → reuse access token → 401
- Integration test: login → logout → reuse refresh token → 401
- Verify blacklist key auto-expire trong Redis sau 15 phút

---

### US-009: Login Rate Limit per IP

> As the system, I want to cap login attempts per IP to prevent brute force, regardless of email tried.

**Acceptance Criteria:**
- 5 requests / 60 seconds / IP cho `POST /v1/auth/login`
- Implementation: Redis SETNX với key `login:rate:{ip}` + INCR + EXPIRE 60s
- 429 Too Many Requests + Retry-After header
- IP lấy từ `X-Forwarded-For` đầu tiên, fallback `RemoteAddr`
- KHÁC với US-004's per-email tracker (3 fails / 15min) — đây là blanket cap

**DoD:**
- Integration test: 6 requests cùng IP → request thứ 6 trả 429
- Integration test: khác IP không bị ảnh hưởng nhau
- ADR ngắn: "X-Forwarded-For trust strategy" (cảnh báo spoofing risk khi chưa có Nginx)

---

### US-010: Product Domain Schema

> As the system, I need a product catalog schema with proper relations and indexes.

**Acceptance Criteria:**
- Flyway V3 migration tạo bảng:
    - `categories` — UUID PK, name UNIQUE, parent_id FK (nullable, self-ref cho cây danh mục)
    - `products` — UUID PK, seller_id FK users, category_id FK categories, sku UNIQUE, name, description, base_price NUMERIC(12,2), status ENUM (DRAFT, ACTIVE, INACTIVE, ARCHIVED), audit fields, deleted_at (soft delete)
    - `product_variants` — UUID PK, product_id FK, sku UNIQUE, name (VD: "Red XL"), price_delta NUMERIC, stock INTEGER (sẽ refactor Sprint 5 với Inventory module)
    - `product_images` — UUID PK, product_id FK, url, sort_order INT, is_primary BOOLEAN
- Indexes:
    - `idx_products_category_status` ON (category_id, status) WHERE deleted_at IS NULL
    - `idx_products_seller_created` ON (seller_id, created_at DESC)
    - `idx_products_sku` UNIQUE
    - `idx_product_variants_product` ON (product_id)
    - `idx_product_images_product_primary` ON (product_id, is_primary) WHERE is_primary = true
- Constraints:
    - check_products_price_non_negative
    - check_variants_stock_non_negative
    - FK seller_id → users(id) ON DELETE RESTRICT (không cho xóa user còn product active)
    - FK category_id → categories(id) ON DELETE RESTRICT
- Seed data: 3 categories root (Electronics, Fashion, Books) cho test
- Entities: Product, Category, ProductVariant, ProductImage extend BaseEntity (trừ join table nếu có)
- Repositories tương ứng

**DoD:**
- 4-5 entity tests cho mỗi entity (basic save + constraint enforcement)
- Schema validate pass (Hibernate ddl-auto=validate)
- Mermaid ERD trong `docs/design/sprint-02-product-schema.md`

---

### US-011: Product Public API

> As a buyer, I want to browse and search products without authentication.

**Acceptance Criteria:**
- `GET /v1/products` (public, no auth)
    - Query params: `page` (default 0), `size` (default 20, max 100), `category` (optional UUID), `status` (default ACTIVE — public không xem DRAFT)
    - Response: Page<ProductSummary> with pagination metadata
    - ProductSummary chỉ chứa: id, sku, name, basePrice, primaryImageUrl, categoryName (NOT description, NOT variants để tránh N+1)
- `GET /v1/products/{id}` (public)
    - Response: ProductDetail with all variants + images
    - Sử dụng `@EntityGraph` hoặc `JOIN FETCH` để tránh N+1
    - 404 nếu product DRAFT/ARCHIVED/deleted
- Zero N+1 query: bật Hibernate Statistics trong test, assert query count

**DoD:**
- Integration test: pagination, filter by category, status filter
- Hibernate Statistics test: list 20 products → ≤ 2 queries (1 count + 1 select), không phải 20+
- Performance baseline: k6 GET /v1/products → p99 (mục tiêu cho sprint 3, chỉ ghi nhận số ở sprint 2)

---

### US-012: Product Admin API with Ownership Check

> As a SELLER, I want to manage my own products. As an ADMIN, I want to manage all products.

**Acceptance Criteria:**
- `POST /v1/admin/products` (SELLER hoặc ADMIN)
    - SELLER tạo → seller_id = caller (lấy từ SecurityContext)
    - ADMIN tạo → có thể chỉ định seller_id (nếu null, dùng caller)
    - Body: sku, name, description, basePrice, categoryId, status (default DRAFT)
    - 201 Created
- `PUT /v1/admin/products/{id}` (SELLER hoặc ADMIN)
    - SELLER chỉ sửa product có seller_id == caller, nếu khác → 403 Forbidden
    - ADMIN sửa được mọi product
- `DELETE /v1/admin/products/{id}` (soft delete via deleted_at)
    - Same ownership rule như PUT
- Validation: sku unique (DB constraint + service check), basePrice >= 0, categoryId tồn tại

**DoD:**
- Integration test matrix:
    - SELLER1 tạo product → OK
    - SELLER1 sửa product của SELLER1 → OK
    - SELLER1 sửa product của SELLER2 → 403
    - ADMIN sửa product của SELLER2 → OK
    - SELLER2 sửa product không tồn tại → 404
- Test SKU duplicate → 409 (DB unique constraint)

---

## Day-by-Day Mapping (Plan)

| Day | Task | Story |
|---|---|---|
| **Tuần 3** | | |
| T2 | Sprint Planning + Product Design Doc | Planning + US-010 design |
| T3 | RBAC @PreAuthorize | US-007 |
| T4 | Logout + token revocation | US-008 |
| T5 | Login rate limit per IP | US-009 |
| T6 | Product schema design (paper/Mermaid) | US-010 design |
| T7 | ADR-002 "JWT vs Session" + ADR-003 "Refresh Token Rotation" | ADRs |
| **Tuần 4** | | |
| T2 | Flyway V3: Product tables | US-010 |
| T3 | Product entity + repo + service | US-010 |
| T4 | GET /v1/products + pagination | US-011 |
| T5 | POST /v1/admin/products + ownership check | US-012 |
| T6 | Hibernate Stats N+1 audit, coverage gate 80% | US-011/012 |
| T7 | Sprint Review + Retro + k6 baseline | Closing |

---

## Capacity Check

**Realistic?** 6 stories, 10 ngày code. Auth hardening (US-007/008/009) ~3 ngày tổng. Product (US-010/011/012) ~5 ngày. Buffer 1 ngày.

**Blockers tiềm năng:**
- JWT filter implementation lần đầu — có thể tốn nửa ngày debug Spring Security 6 filter chain
- N+1 audit — lần đầu dùng Hibernate Statistics, có thể tốn 2-3h tune query
- Pagination test với Testcontainers cần seed data → 1-2h setup TestDataBuilder

**Mitigation:** Nếu Tuần 1 chậm, drop US-009 (rate limit) sang Sprint 3 — không phải core blocker cho Product API.

---

## Definition of Done — Sprint Level

- [ ] 6/6 user stories Done
- [ ] All PRs merged via PR flow
- [ ] CI green on main
- [ ] Coverage ≥ 80% auth (raised from 70%)
- [ ] Coverage gate ACTIVATED for product module from T6 Tuần 4
- [ ] Zero N+1 in Hibernate Statistics test
- [ ] Postman collection updated with new endpoints
- [ ] ADR-002 + ADR-003 merged
- [ ] Sprint Review document
- [ ] Sprint Retro document
- [ ] k6 baseline run for GET /v1/products

---

## Notes & Risks

[Fill during sprint]