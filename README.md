# ecommerce-platform

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

> Prerequisites: Java 21, Maven 3.9+, Docker (for PostgreSQL via Testcontainers)

```bash
# Build all modules
mvn clean install

# Run the app (Sprint 1, Day 4 — after datasource is configured)
mvn -pl app spring-boot:run
```

Database configuration will be added in Sprint 1, Day 4 (`application.yml`).

## Sprint Progress

- [x] Sprint 1 Day 1 — Project scaffold, ADR-001
- [ ] Sprint 1 Day 2 — Domain model, Flyway migrations
- [ ] Sprint 1 Day 3 — JWT auth endpoints
- [ ] Sprint 1 Day 4 — Integration tests with Testcontainers
