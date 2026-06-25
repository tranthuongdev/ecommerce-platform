# Sprint 2 Retrospective

**Sprint:** 2 — Auth Hardening + Product Schema
**Duration:** Tuần 3-4

---

## What worked well

- **PrincipalView interface trong common module** — giải quyết cleanly bài toán product cần principal info mà không phụ thuộc auth. Pattern này sẽ tái sử dụng khi tách microservice ở Year 2.

- **Hibernate Statistics N+1 audit test** — mechanical check, không phụ thuộc visual code review. Catch sớm trước khi production phát hiện.

- **Cleanup scaffolding (RoleTestController) cuối sprint** — production jar không chứa test code. Đặt ngay vào DoD Sprint 2 nên không bị quên.

- **k6 baseline first time chạy** — có số liệu thật, dù số liệu phơi bày gap DoD framework. Thà fail honest còn hơn pass giả.

- **Conventional Commits + PR flow chặt chẽ** — mỗi PR có 1 lý do rõ ràng. Git log đọc lại nhìn được tiến độ.

---

## What didn't work

- **Plan DoD viết generic không account cho BCrypt cost.** Sprint 2 DoD nói "Auth p99 < 50ms" mà BCrypt cost 12 vốn dĩ ~300ms. Gap không phải code, là plan. Cần fix framework, không fix code.

- **k6 threshold ban đầu set quá lỏng (p99 < 500ms)** — không catch được fact rằng login đang 322ms. Lần đầu chạy k6 nên thiếu kinh nghiệm chọn threshold realistic.

- **Day-by-day notes vẫn ngắt quãng** — action item từ Sprint 1 retro nói "update notes mỗi ngày" nhưng vẫn dồn cuối tuần. Pattern lặp lại.

- **Một số task Claude Code generate verify phải sửa nhỏ** — Lettuce Redis disconnect warning, dependencies thiếu, etc. Học bài: đọc lại output của Claude Code không chỉ đếm "BUILD SUCCESS".

---

## What learned

- **BCrypt cost 12 ≈ 300ms p99** trên dev hardware sau JVM warm up. Số thật để dùng cho capacity planning.

- **DoD phải categorize theo loại endpoint** — không thể gộp chung auth/read/write/bcrypt. Sprint 3 sẽ áp dụng.

- **Lua script trong Redis cho atomic INCR+EXPIRE** là pattern cực gọn cho rate limiter. Sẽ dùng lại ở Sprint 17 (Q3 resilience work).

- **PostgreSQL partial unique index** (`WHERE is_primary`) — cách elegant để enforce "max 1 primary image per product" tại DB layer, không cần app logic.

- **Spring Security 6 @AuthenticationPrincipal hoạt động với interface type** — không cần concrete class. Giúp tách module dependency.

- **Surefire reports + JaCoCo HTML report là 2 nguồn truth khi debug coverage gate fail** — không tin con số trong terminal log.

---

## Action Items for Sprint 3

1. **DoD framework update** (T2 Sprint 3): viết ADR phân loại endpoint category với target p99 cho mỗi loại. Apply cho mọi sprint sau.

2. **Day-by-day notes ENFORCE** (mỗi ngày T2-T6): cuối mỗi ngày làm việc, commit notes vào sprint-NN-plan.md. Không để dồn cuối tuần.

3. **k6 threshold theo category từ đầu** (T7 Sprint 3): viết k6 script với threshold tách biệt login/list/detail/admin. Document trong script comment lý do mỗi threshold.

4. **Read Claude Code output kỹ hơn**: sau mỗi prompt, check log warnings/deprecations, không chỉ đếm test pass.

---

## Numbers Sprint 2

[Chạy lệnh sau để fill, paste vào đây]

```powershell
# Số commit Sprint 2
git log --oneline --since="<sprint-2-start-date>" --until="<sprint-2-end-date>" | Measure-Object -Line

# Số PR merged
gh pr list --state merged --search "merged:<start>..<end>" | Measure-Object -Line

# File added
git diff --stat <sprint-2-first-commit>..HEAD -- "*.java"
```

- Commits: [PASTE]
- PRs merged: [PASTE]
- New Java files: [PASTE]
- Total test methods Sprint 2 added: [PASTE]
- Test files added: [PASTE]