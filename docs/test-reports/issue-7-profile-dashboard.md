# Issue #7 — FE 4.2 test and integration report

## Environment and scope

- Frontend branch: `feat/profile-dashboard-issue7`, based on `feat/student-dashboard`.
- API contract read from local `feat/backend` commit `bdf33df` (#6): owner/public profile, avatar/privacy mutations, Student/Teacher/Admin dashboards.
- Frontend: React 19, TypeScript strict, TanStack Query v5, Vite. API responses use `ApiResponse<T>` and query key factories.
- Backend state at this check: separate branch, Spring `mock` profile, in-memory profile/dashboard sources. No durable persistence or production JWT.
- Local environment has Node 24; `java` and `mvn` are not on PATH. Backend-integrated E2E could not run here.

## Implemented frontend checks

| SRS / AC | Frontend coverage | Verification |
| --- | --- | --- |
| FR-PRO-001 AC-01..05 | Owner profile fields; immutable username/email/role; editable name/bio; PNG/JPEG ≤5 MB check; server failure keeps prior profile/avatar | TypeScript build; manual API E2E pending |
| FR-PRO-001 AC-06 | Owner route uses `/users/me/profile`; no account ID supplied by client | Backend permission test pending |
| FR-PRO-002 AC-01..03, AC-07 | Privacy PATCH; public route renders only username/avatar/bio/solved/streak | Backend persistence test pending |
| FR-PRO-002 AC-04..06 | Hidden/missing public profile shows same neutral unavailable state; owner-only toggle | Backend hidden/suspended tests pending |
| FR-PRO-003 AC-01..07 | Student dashboard consumes `/dashboards/student`; data sections, UTC+7, loading/empty/error, no invented links or metrics | Mapper tests; API E2E pending |
| FR-PRO-004 AC-01..07 | Teacher dashboard consumes `/dashboards/teacher`; class/work/submission/notification/contribution sections | API E2E pending |
| FR-PRO-005 AC-01..08 | Admin dashboard consumes `/admin/dashboard`; totals, moderation, activity, audit and chart | API E2E pending |

## Remaining acceptance blockers

1. #6 must merge and replace in-memory profile/dashboard repositories with persistent, role-scoped sources and signed JWT authentication. Current mock data cannot satisfy issue #7 acceptance.
2. Source modules for classes, assignments, quizzes, public courses/practice, notifications, moderation and audit must supply real data. Student course lesson/resume route, class/assignment/detail routes, difficulty breakdown, notification actions and Admin configuration destinations are not available in the current contract; frontend deliberately does not invent these actions.
3. The requested signed-in password change, session management, deactivate account and full notification center are outside the current #6 profile/dashboard API contract. They need their owning module contracts before those workflows can be implemented honestly.
4. Run PRO Test Plan cases plus permission and error E2E against the integrated backend: Student/Teacher/Admin 401/403, private/suspended/deactivated public profiles, invalid/oversize/corrupt avatar, save persistence after reload, UTC+7 boundaries, other-user IDOR, empty/partial data, and keyboard/responsive WCAG checks. Record screenshots and responses before closing #7.

## Verification commands

Run `npm.cmd run build` and `npm.cmd test` from `frontend`. No lint script is configured in this repository. For API contract smoke testing, run #6 backend on port 8080 and `npm.cmd run dev:backend-mock`, then sign in with `student`, `teacher`, or `admin` and password `DemoPass123!`. This mode uses development tokens only; it is not a persistence or security acceptance test.

## Results from this workspace

- `npm.cmd run build`: passed (`tsc -b` and Vite production build).
- `npm.cmd test`: 11/11 passed, including Student dashboard mapping, client-side avatar validation and demo role email aliases.
- `git diff --check`: passed.
- Rendered QA at 1440 px and 390 px: profile, Student/Teacher/Admin dashboards and public profile loaded without horizontal overflow. QA used a temporary local response server shaped like the #6 DTOs; it was removed after inspection. This is visual/API-shape evidence only and does not replace backend E2E.
- No backend E2E result: Java and Maven were unavailable in the current environment, and the #6 branch is mock/in-memory.
- `dev:auth-demo` profile smoke test: owner GET 200; name/bio PATCH persisted within the dev session; privacy ON exposed exactly `username, avatarUrl, shortBio, solved, streak`; invalid read-only `email` PATCH returned 400 and preserved the previous name; privacy OFF returned 404 to anonymous public GET; valid PNG upload succeeded; corrupt PNG returned 415 and preserved the old avatar. These are development-plugin checks, not #6 backend tests.
- `dev:auth-demo` role smoke test: `teacher` and `admin` sign-in returned the matching role; Teacher dashboard showed two sample classes and Teacher profile loaded; Admin dashboard showed sample totals; Admin profile API returned 403. These results validate only the frontend-only demo route and fixture shape.
- Email login correction: `teacher@example.test` and `admin@example.test` both returned their expected demo role/token in HTTP smoke tests; the Username or email field now accepts these explicit aliases.
