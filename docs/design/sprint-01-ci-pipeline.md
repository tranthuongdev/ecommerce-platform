# CI Pipeline Design (US-006)

**Date:** 2026-06-26
**Related:** US-006

## Goal
GitHub Actions CI chạy trên mọi PR vào main + push feature branches.
Coverage gate 70% trên auth module (sẽ tăng 80% từ Sprint 2).

## Decisions

| # | Decision | Choice | Rationale |
|---|---|---|---|
| 1 | CI provider | GitHub Actions | Native với repo, free cho private repo, không cần extra setup |
| 2 | Java distribution | Temurin (Eclipse) | LTS, dùng giống local dev (consistent) |
| 3 | Build command | mvn -B clean verify | -B (batch mode) cho CI log gọn; verify chạy cả test + JaCoCo check |
| 4 | Coverage tool | JaCoCo 0.8.12 | De-facto standard Java coverage, tích hợp Maven tốt |
| 5 | Coverage gate scope | Chỉ auth module | common chưa có code, app chỉ entrypoint. Tránh false sense of coverage |
| 6 | Coverage threshold | 70% Sprint 1, 80% từ Sprint 2 | Sprint 1 mới setup, ramp-up dần |
| 7 | Artifact retention | JaCoCo report + Surefire (on failure) | Debug khi CI fail, không cần keep mãi |
| 8 | Pipeline target time | < 5 phút | Feedback loop nhanh, dev không context-switch |

## What's NOT in this iteration

- SonarCloud (Sprint 12, Q2 closing)
- Trivy image scan (Sprint 6, Q1 closing)
- Dependency scanning Dependabot (auto, không cần config)
- Multi-OS matrix (chỉ ubuntu-latest cho Year 1)

## Verification

1. Push branch → CI trigger
2. mvn verify pass với coverage check
3. JaCoCo HTML report download được từ Actions artifact
4. CI fail nếu coverage < 70% (verify bằng test với mock fail)
5. Pipeline < 5 phút end-to-end