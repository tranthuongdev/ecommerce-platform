# ADR-002: JWT vs Server Session for Authentication

**Date:** 2026-07-11
**Status:** Accepted
**Related:** ADR-001, US-004, US-008

## Context

Sprint 1 phải chọn cơ chế quản lý trạng thái xác thực:
1. **Server-side session** — Spring Session + Redis, session ID trong cookie
2. **JWT stateless** — token tự chứa claims, signed
3. **Hybrid** — JWT cho access, session cho long-lived state

Year 1 yêu cầu: 5,000 RPS, multi-instance (Sprint 21 K8s HPA), mobile clients (Year 2 SaaS).
Quyết định này quan trọng vì ảnh hưởng tất cả endpoint auth, refresh flow, logout, scale strategy.

## Decision

Chọn **JWT stateless cho access token**, **DB-stored refresh token** với rotation.

Specifics:
- Access token: JWT HS512, TTL 15 phút, claims (sub, roles, iat, exp, jti)
- Refresh token: random 256-bit, SHA-256 hash stored in DB, TTL 7 ngày
- Stateless filter validate JWT mỗi request — không hit DB cho auth check
- Logout: blacklist access jti trong Redis (TTL = remaining token TTL), revoke refresh trong DB

## Consequences

### Positive
- **Horizontal scale dễ:** mỗi instance verify JWT độc lập, không shared state
- **Performance:** không DB lookup mỗi request, scale linear với CPU
- **Mobile-friendly:** không phụ thuộc cookie, hoạt động qua header
- **Microservice-ready:** Year 2 mỗi service tự verify, không cần auth service call
- **Refresh rotation:** detect token compromise (reuse → revoke all)

### Negative
- **Logout không instant không có blacklist:** mitigated bằng Redis blacklist (US-008)
- **Token không thể chỉnh sửa khi đã issue:** đổi role phải đợi hết TTL hoặc force logout
- **JWT size lớn hơn session ID:** 500-800 bytes vs 32 bytes per request — chấp nhận được cho HTTP/2
- **Secret rotation phức tạp:** đổi secret = revoke all tokens. Sprint 18 sẽ giải quyết với Vault.

### Neutral
- Defensive user lookup trong filter (US-007): catch revoked users post-token-issue. Trade-off DB hit lấy security. Sprint 7 cache sẽ giảm cost.

## Alternatives Considered

### Alt 1: Server-side session (Spring Session + Redis)
- **Rejected:** scale theo Redis throughput. Mỗi request 1 Redis call → bottleneck ở 5,000 RPS target.
- Hợp khi: monolith nhỏ, web-only, không mobile.

### Alt 2: Opaque token (random string, DB lookup)
- **Rejected:** mỗi request 1 DB query. Khó scale, defeat stateless principle.
- Hợp khi: cần revoke instant, sẵn DB throughput cao.

### Alt 3: PASETO thay JWT
- **Rejected:** ecosystem nhỏ hơn JWT. JJWT library mature. PASETO security advantages không quan trọng với HS512.

### Alt 4: Hybrid (JWT 1h + session for sensitive ops)
- **Rejected:** complexity 2 mechanism. JWT 15m + refresh rotation đủ.

## Implementation

- Sprint 1 US-004: JWT generator HS512
- Sprint 1 US-005: refresh rotation
- Sprint 2 US-007: filter validate JWT
- Sprint 2 US-008: blacklist + logout
- Sprint 18: secret rotation via Vault dynamic