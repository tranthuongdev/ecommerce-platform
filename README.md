# ecommerce-platform

![CI](https://github.com/tranthuongdev/ecommerce-platform/actions/workflows/ci.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-70%25%20gate-blue)

Modular monolith e-commerce platform. Solo dev — Year 1 target: **5,000 RPS** end of Q4.
Year 2 plan: Strangler Fig migration to microservices per [ADR-001](docs/adr/ADR-001-modular-monolith.md).

## Project Overview

| Module   | Role                                                  |
|----------|-------------------------------------------------------|
| `common` | Shared utilities (DTOs, exceptions, value objects)    |
| `auth`   | Authentication & authorization domain (JWT, RBAC)     |
| `app`    | Spring Boot entry point — wires all modules together  |

Dependency direction (one-way):

```
app → auth → common
app → common
```

## Tech Stack

| Layer          | Technology                              |
|----------------|-----------------------------------------|
| Language       | Java 21                                 |
| Framework      | Spring Boot 3.4.0                       |
| Security       | Spring Security (JWT — Sprint 1)        |
| Persistence    | Spring Data JPA + PostgreSQL            |
| Migrations     | Flyway                                  |
| Build          | Maven multi-module                      |
| Observability  | Spring Actuator (Prometheus — Sprint 3) |
| Testing        | JUnit 5 + Testcontainers               |

## How to Run Locally

### Prerequisites
- JDK 21, Maven 3.9+, Docker Desktop

### Setup
1. Copy environment file: `cp .env.example .env`
2. Start infrastructure: `docker compose up -d`
3. Wait for healthy status: `docker compose ps`
4. Build project: `mvn clean install`
5. Run app: `cd app && mvn spring-boot:run`
6. Verify: `curl http://localhost:8080/actuator/health`

### Stop
- `docker compose down` (preserves data)
- `docker compose down -v` (wipes all data)

## Sprint Progress

- [x] Sprint 1 Day 1 — Project scaffold, ADR-001
- [ ] Sprint 1 Day 2 — Domain model, Flyway migrations
- [ ] Sprint 1 Day 3 — JWT auth endpoints
- [ ] Sprint 1 Day 4 — Integration tests with Testcontainers
