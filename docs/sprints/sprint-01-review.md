# Sprint 1 Review — Project Setup + Auth Skeleton

**Sprint dates:** 2026-06-22 → 2026-07-04
**Sprint Goal:** Người dùng có thể đăng ký, đăng nhập, refresh token — full flow qua Postman, integration test xanh, CI pipeline pass.

## Outcome: ✅ Sprint Goal Achieved

All 6 user stories Done.

## Stories Delivered

| ID | Title | Status | PR |
|---|---|---|---|
| US-001 | Project Skeleton & Local Dev Environment | ✅ Done | [#] |
| US-002 | Database Schema for Users & Roles | ✅ Done | [#] |
| US-003 | User Registration | ✅ Done | [#] |
| US-004 | User Login & JWT Issuance | ✅ Done | [#] |
| US-005 | Refresh Token Rotation | ✅ Done | [#] |
| US-006 | CI Pipeline & Quality Gate | ✅ Done | [#] |

## Metrics

| Metric | Target | Actual |
|---|---|---|
| Test count | — | 36 (33 auth + 3 app) |
| Test classes | — | 9 |
| Auth coverage (LINE) | ≥ 70% | 98%+ |
| Local mvn verify | — | 1m 11s |
| CI pipeline duration | < 5 min | 1m 34s |
| Flyway migrations | — | V1, V2 (clean) |
| Docker image size | — | (deferred to Sprint 6 hardening) |

## Architecture Decisions

- ADR-001: Modular Monolith vs Microservices

## Deliverables

- ✅ 3 Maven modules (common, auth, app), package-by-feature
- ✅ Docker Compose (postgres 17-alpine, redis 7-alpine) with healthchecks
- ✅ Flyway V1 + V2 (users/roles/user_roles + refresh_tokens)
- ✅ BaseEntity + JPA Auditing
- ✅ Spring Security stateless config, BCrypt cost 12
- ✅ JWT HS512, 15m access TTL, 7d refresh TTL
- ✅ Refresh token rotation with **reuse detection** (revokes all on reuse)
- ✅ Login rate limit (3 fails / 15min / email via Redis)
- ✅ RFC 7807 error contract with GlobalExceptionHandler
- ✅ Postman collection for /v1/auth/*
- ✅ GitHub Actions CI with JaCoCo 70% gate

## Demo Flow

```bash
# Register
curl -X POST /v1/auth/register -d '{"email":"demo@test.com","password":"secret123","fullName":"Demo"}'
# → 201

# Login
curl -X POST /v1/auth/login -d '{"email":"demo@test.com","password":"secret123"}'
# → 200 with {accessToken, refreshToken, expiresIn:900}

# Refresh
curl -X POST /v1/auth/refresh -d '{"refreshToken":"<old>"}'
# → 200 with new tokens, old refresh revoked

# Reuse detection
curl -X POST /v1/auth/refresh -d '{"refreshToken":"<same-old>"}'
# → 401 type=refresh-token-reuse, ALL user tokens revoked
```

## Tech Debt Logged

- Lettuce Redis disconnect warning in test log (issue #[N])

## Self-defense Q&A (trade-offs)

**Q: Tại sao chọn JWT HS512 thay vì RS256?**
A: HS512 đủ secure cho monolith (single secret), simpler key management.
RS256 sẽ cần thiết khi tách microservices (Year 2) — public key verification ở downstream services.

**Q: Tại sao refresh token lưu DB thay vì stateless JWT?**
A: Refresh token cần revocation capability (logout, reuse detection). Stateless JWT không revoke được trừ blacklist (cũng cần state).

**Q: Tại sao 3 fail thì lock thay vì 5 hay 10?**
A: Spec US-004 AC explicit. Sprint 17 (Q3) sẽ implement full rate limiter sliding window có metrics để tune.

**Q: Tại sao chỉ track fail by email không track by IP?**
A: Simplicity for Sprint 1. IP-based rate limit cần X-Forwarded-For handling (Nginx layer) — Sprint 17 scope.