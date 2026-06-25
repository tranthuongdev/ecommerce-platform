# ADR-003: Refresh Token Rotation with Reuse Detection

**Date:** 2026-07-11
**Status:** Accepted
**Related:** ADR-002, US-005

## Context

Refresh token là "long-lived credential" — TTL 7 ngày. Nếu bị steal, attacker có 7 ngày access.

3 chiến lược:
1. **Static refresh:** 1 token dùng mãi trong 7 ngày
2. **Rotation:** mỗi /refresh issue token mới, revoke token cũ
3. **Rotation + reuse detection:** rotation + nếu token cũ được dùng lại → revoke TOÀN BỘ token user

Year 1 mục tiêu production-grade security.

## Decision

Chọn **Rotation + Reuse Detection** (chiến lược 3).

Flow:
- Issue: random 256-bit, SHA-256 hash lưu DB, return plaintext cho client (chỉ 1 lần)
- Rotate: client gửi refresh1 → server tạo refresh2 + access2, revoke refresh1, set replaced_by = refresh2.id
- Reuse detect: nếu refresh1 (đã revoked) gửi lại → đây là dấu hiệu compromise (attacker dùng token cũ HOẶC race condition):
    - Revoke ALL tokens của user
    - Throw exception riêng (log WARN với userId cho security audit)
    - User phải login lại
- Storage: hash, không plaintext. Nếu DB leak, token không dùng được.

## Consequences

### Positive
- **Detect compromise:** OAuth 2.0 BCP recommended (RFC 6749 + draft-ietf-oauth-security-topics)
- **Smaller window:** mỗi refresh giảm "long-lived credential" thành "single-use"
- **Forensic trail:** replaced_by FK cho phép trace token chain khi điều tra
- **DB leak protection:** hash storage = stolen DB ≠ stolen tokens

### Negative
- **False positive:** race condition khi client double-call /refresh → revoke all (user phải login lại)
    - Mitigation: client phải có mutex local; UI hiển thị "session expired, login again"
    - Acceptable cost cho security guarantee
- **DB write mỗi rotation:** mỗi /refresh = 2 DB write (revoke old + insert new). 5,000 RPS ↔ acceptable.
- **Storage growth:** mỗi user 1 refresh chain. Cleanup job sẽ cần ở Sprint 16 (DR Sprint).

### Neutral
- replaced_by chain có thể dài. Cleanup expired tokens (revoked_at < now - 30d) sẽ giữ DB compact.

## Alternatives Considered

### Alt 1: Static refresh (no rotation)
- **Rejected:** stolen refresh = 7 ngày attack window. Không acceptable production.

### Alt 2: Rotation không reuse detection
- **Rejected:** giảm window nhưng không phát hiện được compromise. Attacker dùng refresh1 trước user, user dùng sau → reject silent, attacker tiếp tục với refresh2.
- Reuse detection chính là cái phân biệt user vs attacker khi race.

### Alt 3: Sliding TTL (extend on every use)
- **Rejected:** never-expiring token nếu user active liên tục. Defeat purpose của TTL.

### Alt 4: Token binding (DPoP, mTLS)
- **Rejected:** complexity cao, client support kém. Reserve for Year 2 multi-tenant if needed.

## Implementation

- Sprint 1 US-005: implemented
- Verified: 3 IT tests pass (rotate valid, detect reuse + revoke all, reject expired)
- Sprint 16 (DR): add scheduled cleanup of revoked tokens > 30 days

## Reference

- RFC 6749 (OAuth 2.0)
- draft-ietf-oauth-security-topics — Section 4.12 (Refresh Token Replay Detection)
- Auth0 blog: "Refresh Token Rotation"