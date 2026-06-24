# Sprint 1 — Project Setup + Auth Skeleton

**Duration:** Tuần 1-2 (Sprint 1 of 24)
**Start date:** 2026-06-22
**End date:** 2026-07-04
**Sprint Goal:** Người dùng có thể đăng ký tài khoản, đăng nhập bằng email/password, và refresh token — toàn bộ flow chạy được qua Postman, có integration test xanh, CI pipeline pass trên mọi PR.

---

## User Stories Committed

### US-001: Project Skeleton & Local Dev Environment

> As a developer, I want a multi-module Maven project running with Docker Compose, so that I can start coding business logic without infra blockers.

**Acceptance Criteria:**
- Repo có structure: `auth/`, `common/`, `app/` modules + parent `pom.xml`
- `docker compose up -d` khởi động được postgres + redis healthy
- `mvn clean install` chạy pass trên cả 3 module
- README có instruction "How to run locally" trong < 10 dòng
- App khởi động được, expose `GET /actuator/health` trả 200

**Definition of Done:**
- Code merged vào `main` qua PR
- CI workflow `.github/workflows/ci.yml` xanh (lint + build + test)
- Branch protection enabled
- ADR-001 "Modular Monolith vs Microservices" merged

---

### US-002: Database Schema for Users & Roles

> As a system, I need a user/role schema with constraints enforced at DB layer, so that data integrity không phụ thuộc vào application code.

**Acceptance Criteria:**
- Flyway migration `V1__create_users_and_roles.sql` chạy được
- Tables: `users`, `roles`, `user_roles` với FK + check constraint (email format, password_hash NOT NULL)
- Default 3 roles seed: USER, ADMIN, SELLER
- Unique constraint trên `users.email` (case-insensitive)
- Test verify constraint hoạt động (insert invalid → fail at DB layer, không phải app layer)

**Definition of Done:**
- Migration test với Testcontainers passes
- Schema diagram trong `docs/design/sprint-01-auth-schema.md` (ERD đơn giản dạng Mermaid)

---

### US-003: User Registration

> As a new user, I want to register with email + password, so that I can have an account on the platform.

**Acceptance Criteria:**
- `POST /v1/auth/register` với body `{email, password, fullName}`
- Bean Validation: email format, password ≥ 8 ký tự, fullName không rỗng
- Password hash bằng BCrypt (cost factor 12)
- Email duplicate → 409 Conflict với error contract RFC 7807
- Validation fail → 400 với error chi tiết từng field
- Response 201 Created, **không return password hoặc hash**
- Default role USER được gán tự động

**Definition of Done:**
- Integration test với Testcontainers: happy path + 3 edge cases (duplicate email, invalid email, weak password)
- Postman collection có example request/response

---

### US-004: User Login & JWT Issuance

> As a registered user, I want to login with email/password and receive a JWT, so that I can access protected endpoints.

**Acceptance Criteria:**
- `POST /v1/auth/login` với `{email, password}`
- Verify password bằng BCrypt
- Sai password 3 lần trong 15 phút → 429 Too Many Requests (rate limit cơ bản, full version ở Sprint 2)
- Response: `{accessToken, refreshToken, expiresIn}`
- Access token JWT 15 phút, chứa claims: `sub` (userId), `roles`, `iat`, `exp`
- Refresh token random 256-bit, lưu DB với `expires_at = now() + 7 days`, hash trước khi store
- Sai credential → 401 Unauthorized, **không phân biệt** "email không tồn tại" vs "sai password" (security best practice)

**Definition of Done:**
- Integration test happy + 3 edge cases
- ADR-002 "JWT vs Session" merged

---

### US-005: Refresh Token Rotation

> As a logged-in user, I want my session to extend seamlessly via refresh token, so that I don't have to login every 15 phút.

**Acceptance Criteria:**
- `POST /v1/auth/refresh` với `{refreshToken}`
- Verify refresh token còn valid (chưa expired, chưa revoked)
- **Rotation:** issue new access token + new refresh token, revoke old refresh token
- Reuse old refresh token sau khi rotation → 401 + revoke toàn bộ token chain của user (security: nghi token bị compromise)
- Refresh token expired → 401, user phải login lại

**Definition of Done:**
- Integration test: happy path, reuse detection (rotation security), expired token
- ADR-003 "Refresh Token Rotation Strategy" merged
- Sequence diagram cho rotation flow trong `docs/design/`

---

### US-006: CI Pipeline & Quality Gate

> As a developer, I want every PR to be automatically tested and validated, so that broken code never reaches main.

**Acceptance Criteria:**
- GitHub Actions workflow trigger trên: PR vào main + push vào feature branches
- Steps: setup JDK 21 → cache Maven → `mvn verify` → JaCoCo report
- JaCoCo coverage gate: **fail nếu coverage < 70% trên module `auth`** (sẽ tăng lên 80% từ Sprint 2)
- Branch protection rule: require CI pass + 1 review (self-review tính, có thể bypass nhưng phải tick box)
- Pipeline thời gian < 5 phút
- README có badge build status

**Definition of Done:**
- Test fake PR thử fail coverage → CI block merge
- ADR (nếu cần) cho lựa chọn CI tool (skip nếu đơn giản)

---

## Day-by-Day Mapping

| Ngày | Task | User Story |
|---|---|---|
| **T2 Tuần 1** | Sprint Planning + Setup môi trường | Planning |
| T3 | Init Spring Boot multi-module Maven | US-001 |
| T4 | Docker Compose postgres + redis | US-001 |
| T5 | Flyway V1 schema | US-002 |
| T6 | GitHub Actions CI cơ bản | US-006 (partial) |
| T7 | ADR-001 + Self review | ADR + cleanup |
| **T2 Tuần 2** | Spring Security + JWT generator | US-004 (foundation) |
| T3 | POST /v1/auth/register | US-003 |
| T4 | POST /v1/auth/login + BCrypt | US-004 |
| T5 | Refresh token rotation | US-005 |
| T6 | Testcontainers integration tests | Wrap-up + US-006 |
| T7 | Sprint Review + Retro | Demo + Video + Retro |

---

## Capacity Check

**Realistic?** 6 stories trong ~7 ngày code thực sự (~1.2 days/story). Auth flow tương đối standard, nhiều tutorial reference. **OK.**

**Blockers tiềm năng:**
- Lần đầu Testcontainers → buffer 2-3h debug
- Lần đầu GitHub Actions với JaCoCo gate → buffer 4h
- Spring Security 6 syntax khác Spring Security 5 → đọc docs 30 phút trước khi code

**Dependencies bên ngoài:** Không. Tất cả local.

**Mitigation:** Dành buffer 0.5 ngày trong Tuần 2 cho debug. Nếu Tuần 1 chậm, drop US-005 (refresh rotation) sang Sprint 2 thay vì cắt test/ADR.

---

## Definition of Done — Sprint Level

Sprint 1 chỉ được đóng khi tất cả các điều sau ĐỀU đạt:

- ✅ 6/6 user stories ở trạng thái Done trên board
- ✅ Tất cả PRs đã merge vào main
- ✅ CI pipeline xanh trên main
- ✅ Coverage ≥ 70% module auth (JaCoCo report attached)
- ✅ Postman collection export vào `docs/postman/auth.postman_collection.json`
- ✅ 3 ADR (001, 002, 003) merged trong `docs/adr/`
- ✅ Sprint Review video (10-15 phút) record + lưu link YouTube unlisted
- ✅ Sprint Retro file `docs/retros/sprint-01-retro.md` viết xong
- ✅ Sprint 2 plan đã chuẩn bị (file `docs/sprints/sprint-02-plan.md`)

---