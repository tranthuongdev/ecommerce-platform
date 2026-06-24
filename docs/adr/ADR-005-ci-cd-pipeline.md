# ADR-005: CI Pipeline Tooling and Strategy

**Date:** 2026-06-26
**Status:** Accepted
**Related:** ADR-001, US-006

## Context

Sprint 1 onwards, every PR must pass automated checks before merge to main.
Branch protection rule already requires status checks (set in T2).

Year 1 CI requirements:
- Sprint 1-6: build + test + coverage gate
- Sprint 6 (Q1 closing): + Trivy image scan
- Sprint 12 (Q2 closing): + SonarCloud quality gate + deploy staging
- Sprint 18 (Q3): + dependency vulnerability scan
- Sprint 24 (Q4): + canary deploy automation

ADR-005 locks down the foundation (CI provider, build tool, coverage tool)
to avoid mid-year migration costs.

## Decision

### Provider: GitHub Actions
- Native integration with repo
- Free tier sufficient for solo Year 1 work
- YAML-based workflow, version-controlled in repo

### Build Tool: Maven (already chosen ADR-001)
- mvn -B clean verify as canonical build command
- Maven cache enabled in actions/setup-java for speed

### Coverage Tool: JaCoCo
- 0.8.12+ (Java 21 support)
- Coverage gate via maven-enforcer / jacoco-maven-plugin check goal
- Initial threshold 70%, ramp to 80% from Sprint 2

### Workflow Structure
- Single job "build" for Sprint 1
- Will split into matrix (build + scan + deploy) from Sprint 11
- Triggers: PR to main, push to feat/* and fix/* branches
- No scheduled runs (cost optimization)

### Coverage Scope
- Only modules with business logic get coverage gate
- Sprint 1: only `auth` (common/app excluded — common is shared infra,
  app is just entrypoint)
- Add modules to gate as they grow (Sprint 2: + product when created)

## Consequences

### Positive
- Fast feedback loop (< 5 min target)
- Reproducible builds (no "works on my machine")
- Coverage gate prevents test regression
- Foundation for adding security/quality scans incrementally

### Negative
- GitHub Actions vendor lock-in (acceptable, easy to migrate if needed)
- 70% threshold may feel low — intentional ramp-up
- No multi-OS testing (acceptable — production is Linux only)

### Neutral
- Coverage % is a proxy metric, not quality guarantee
- Will add mutation testing (PIT) in Q2 if time permits

## Alternatives Considered

- **CircleCI/Jenkins:** Rejected — extra setup, no benefit over GH Actions for this scale
- **Gradle:** Rejected — Maven already chosen, no migration value
- **Coverage gate 80% from start:** Rejected — punishes Sprint 1 setup work
- **No coverage gate, just report:** Rejected — without enforcement, coverage drifts down

## Evolution

- Sprint 2: threshold to 80%
- Sprint 6: + Trivy
- Sprint 12: + SonarCloud + staging deploy
- Sprint 24: + production deploy gate