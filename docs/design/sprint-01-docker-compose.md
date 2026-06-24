# Design Doc: Docker Compose Setup (Sprint 1, T4)

**Date:** 2026-06-24
**Author:** Trần Thưởng
**Related:** US-001

## Goal

Setup local dev environment with `docker compose up -d` starting:
- PostgreSQL 17 (alpine)
- Redis 7 (alpine)

Both services must be healthy and accessible from native Spring Boot app running via IntelliJ.

## Decisions

### Database choice
- **Postgres 17 alpine** — small image, latest LTS, support to 2029
- Trade-off: musl libc vs glibc — acceptable for standard extensions

### Redis choice
- **Redis 7 alpine** — has ACL, Functions (will use in Sprint 17 Q3)
- Trade-off: skip Redis 8 due to license transition uncertainty

### Volume strategy
- **Named volumes** — Docker-managed, Windows-friendly, persist between `down`
- Cleanup: `docker compose down -v` to wipe data

### Network
- Single bridge network `ecommerce_net`
- Service name = DNS name within network
- Ports forwarded to host for native app access

### Healthcheck
- Postgres: `pg_isready -U <user> -d <db>`
- Redis: `redis-cli ping`
- Start period 10s for Postgres (data dir init)

### Secrets
- `.env` for local (gitignored)
- `.env.example` for documentation (committed)
- Migration to Vault planned in Sprint 18 (Q3)

### Hibernate DDL strategy
- `ddl-auto: validate` — Flyway is single source of truth for schema
- NOT `update` (data loss risk), NOT `create-drop` (dev-only anti-pattern)

## Verification

1. `docker compose up -d` → 2 services healthy in <30s
2. `psql -h localhost -p 5432 -U ecommerce -d ecommerce` connects
3. `redis-cli -h localhost ping` returns PONG
4. App starts via IntelliJ, no connection errors
5. `mvn verify` passes `IntegrationSmokeTest` validating both connections
6. After `docker compose down` (without -v), restart preserves data