# Sprint 1 Retrospective

**Date:** 2026-07-04

## What worked well

- Design-first pattern (Design Doc → ADR → Code) caught issues before code
- Solo Scrum cadence (T2 plan, T7 retro) gave structure to the week
- Claude Code prompts với specs chi tiết → ít back-and-forth khi generate
- Testcontainers @ServiceConnection — Spring Boot 3.1+ tự config, đỡ boilerplate
- Branch protection forced PR flow ngay từ commit đầu — không skip review nào

## What didn't work

- ADR ordering nhầm — viết ADR-004/005 sai topic so với plan, phải xóa
- T2 đầu sprint skipped Sprint Planning ban đầu, suýt vào code thẳng
- Lettuce Redis disconnect warning xuất hiện từ T4, defer fix → vẫn còn ở T6 retro
- Có lúc Claude verify hộ trong prompt → conflict với own verify step

## What I learned

- ADR numbering chains qua nhiều sprint, cần check plan trước khi viết
- Hibernate `@SQLRestriction` cleaner hơn manual filter, có từ 6.3+
- BCrypt max 72 bytes (constraint kỹ thuật), nên có @Size(max=72) trên password
- Refresh token rotation với reuse detection = phải revoke **toàn bộ chain** không chỉ token bị reuse
- Spring Data Redis warning về "Could not safely identify store" — vì JPA repo nằm trong package được scan bởi Redis. Cần exclude.

## Action items for Sprint 2

1. Trước khi viết bất kỳ ADR/Design Doc nào, check Year_1_Weekly_Plan.docx để xác nhận đúng sprint/day
2. Fix Spring Data Redis repository scan warning (exclude pattern hoặc explicit base package)
3. Sprint 2 sẽ chạm Redis cache mạnh — fix Lettuce disconnect issue cùng lúc, không defer tiếp
4. Setup commit message linting (commitlint) để enforce Conventional Commits automatically

## Sprint health

- Velocity: 6/6 stories (100%)
- Burnout level: low — pacing tốt
- Confidence cho Sprint 2: high — foundation chắc