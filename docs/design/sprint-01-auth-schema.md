# Auth Schema Design (US-002)

**Date:** 2026-06-25
**Author:** Trần Thưởng
**Related:** US-002, ADR-004

## Goal

Design and implement the foundational database schema for the auth domain:
- Users (registered accounts)
- Roles (USER, ADMIN, SELLER)
- User-Role assignment (many-to-many)

This schema is the foundation for 6+ entities and 23 sprints to come. Decisions
here will be hardest to refactor later.

## Decisions Summary

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | Primary Key | UUID v4 (gen_random_uuid()) | Year 2 multi-tenant + Kafka event safety. v7 deferred to Q3 |
| 2 | Naming | snake_case DB / camelCase Java | Postgres community standard |
| 3 | Soft delete | Yes for users only | Audit + GDPR compliance |
| 4 | Audit fields | created_at, updated_at, created_by, updated_by, version | All entities via BaseEntity |
| 5 | Email uniqueness | Case-insensitive via functional index | Defense in depth (app + DB layer) |
| 6 | Role model | 3-table RBAC (users + roles + user_roles) | Support multi-role per user |
| 7 | Index strategy | Index from day 1 (FK, email functional, partial active) | Avoid retroactive migration |
| 8 | Migration location | auth/src/main/resources/db/migration/ | Module-scoped, ready for microservice extract |

## ERD

\`\`\`mermaid
erDiagram
USERS {
UUID id PK
VARCHAR email UK "case-insensitive"
VARCHAR password_hash
VARCHAR full_name
BOOLEAN enabled "default true"
TIMESTAMP deleted_at "nullable, soft delete"
TIMESTAMP created_at
UUID created_by
TIMESTAMP updated_at
UUID updated_by
BIGINT version "optimistic locking"
}

    ROLES {
        UUID id PK
        VARCHAR name UK "USER/ADMIN/SELLER"
        VARCHAR description
        TIMESTAMP created_at
    }

    USER_ROLES {
        UUID user_id PK,FK
        UUID role_id PK,FK
        TIMESTAMP assigned_at
    }

    USERS ||--o{ USER_ROLES : "has"
    ROLES ||--o{ USER_ROLES : "assigned to"
\`\`\`

## Indexes

- `uq_users_email_lower` — UNIQUE on LOWER(email), case-insensitive uniqueness
- `idx_users_active` — PARTIAL INDEX on (id) WHERE deleted_at IS NULL, optimize for 90%+ queries
- `idx_user_roles_role_id` — Reverse lookup "users with role X"

## Constraints (DB-level, defense in depth)

- `chk_users_email_format` — regex check, blocks bad data even if app layer is bypassed
- `chk_users_full_name_length` — non-empty after trim
- `fk_user_roles_user ON DELETE CASCADE` — clean up role assignments when user hard-deleted
- `fk_user_roles_role ON DELETE RESTRICT` — prevent role deletion if assigned

## Why 3-table RBAC over single-role enum

E-commerce reality: users often have multiple roles (USER buying + SELLER selling).
Single-role enum forces awkward "primary role" logic. Multi-table RBAC:
- Adding role = INSERT, no code deploy
- Foundation for Year 2 multi-tenant permission system
- Industry-standard pattern (Spring Security expects multi-role)

## Why UUID over BIGINT

- Year 2 multi-tenant: BIGINT requires coordination, UUID does not
- Kafka events (Sprint 8+): UUID prevents ID collision across producers
- Public-facing IDs (URLs): UUID prevents enumeration attacks
- Trade-off: 16 bytes vs 8 bytes, index fragmentation. Acceptable for Year 1.
- Migration to UUID v7 (sortable, index-friendly) deferred to Q3 Sprint 13.

## Verification Plan

1. Flyway V1 migration runs on fresh DB, success in `flyway_schema_history`
2. 3 default roles seeded (USER, ADMIN, SELLER)
3. Repository tests pass:
    - Save user with role assignment
    - Reject duplicate email case-insensitive (`test@x.com` vs `TEST@X.COM`)
    - Reject invalid email format at DB level
    - Soft-deleted user not returned by findById
4. Schema visible via `psql \d users` with all indexes/constraints
5. Hibernate Statistics in test log: zero N+1 query