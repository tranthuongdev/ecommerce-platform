# ADR-001: Modular Monolith with Package-by-Feature Structure

**Date:** 2026-06-27
**Status:** Accepted
**Deciders:** Trần Thưởng

## Context

Năm 1 Hell Training Protocol yêu cầu build E-commerce Platform với mục tiêu 5,000 RPS cuối Q4. Hai approach chính:

1. **Microservices từ đầu** — mỗi domain (auth, product, order) là service riêng
2. **Monolith truyền thống** — 1 module duy nhất, tất cả code chung
3. **Modular Monolith** — 1 deployment unit nhưng tách module theo business domain

Yếu tố ảnh hưởng:
- Solo developer, không có team
- Năm 2 sẽ tách microservices (Strangler Fig Pattern)
- Phải scale đến 5,000 RPS nhưng không phải distributed system từ đầu

## Decision

Chọn **Modular Monolith** với cấu trúc Maven multi-module:
- 3 module ban đầu: `common`, `auth`, `app`
- Thêm module khi có feature mới (Sprint 2: product, cart; Sprint 4: order, payment, inventory)
- Bên trong mỗi module: **package-by-feature**, không phải package-by-layer
- Dependency direction một chiều: `app → business modules → common`
- `common` không phụ thuộc gì ngoài framework chung (Spring, Jakarta)

## Consequences

### Tích cực

- Tránh complexity của distributed system khi chưa cần (network failure, distributed tx, deployment overhead)
- Vẫn có ranh giới module bắt buộc bởi Maven → không bị "big ball of mud"
- Refactor/test nhanh: 1 codebase, 1 IDE project
- Sẵn sàng cho Strangler Fig: mỗi module có thể tách thành service ở Năm 2
- Package-by-feature match business model → onboarding mới (hoặc bản thân 6 tháng sau) dễ hiểu

### Tiêu cực

- Build time tăng theo số module (mitigate: parallel build, build cache)
- Dễ vi phạm dependency direction nếu không kỷ luật (mitigate: ArchUnit test ở Sprint 2+)
- Cần discipline khi thêm code vào `common` — không phải mọi shared code đều thuộc common

### Trung tính

- Không tận dụng được advantage của microservices (scale từng service độc lập), nhưng đó là design choice có chủ ý cho Năm 1

## Alternatives Considered

1. **Single-module Spring Boot:** Reject — không có boundary enforcement, sẽ thành mud ball trong 3 tháng
2. **Microservices từ Sprint 1:** Reject — solo dev không kham nổi distributed system overhead khi chưa có business validation
3. **Package-by-layer trong single module:** Reject — anti-pattern hiện nay, code 1 feature bị xé thành 3-4 chỗ

## References

- "Building Microservices" — Sam Newman, Chapter 1 (Modular Monolith section)
- Hell Training Protocol v2.0 — Quarter 1 plan
- Spring Boot reference: structuring your code