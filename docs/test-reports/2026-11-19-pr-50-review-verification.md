# PR #50 Review Resolution Verification Report

- **Date:** 2026-11-19
- **Branch:** `feat/srs-4-1-4-6-integration`
- **Review Reference:** [PR #50 review `5963315338`](https://github.com/hienpham0344/AlgoLearner/pull/50#pullrequestreview-5963315338) (`fcbcdda`)
- **Verified Range:** `fcbcdda..21bc454` (backend source unchanged since `69fec1c`; frontend verified at `21bc454`)
- **Implementation Plan:** [`2026-11-19-pr-50-review-resolution.md`](../plans/2026-11-19-pr-50-review-resolution.md)

---

## 1. Review Findings Resolution Summary

| Finding | Severity | Resolution Summary | Key Commits |
| :--- | :--- | :--- | :--- |
| **`[BLOCKER-1]` SRS 4.5 Lesson UI vs Backend Contract** | Blocker | Added `DELETE .../materials/{materialId}`, `GET .../lessons/capabilities`, `GET .../lessons/activities`, enriched `LessonDetailView`/`LinkedItemView` with student-safe gating, fixed `AUTO_ITEMS` -> `MANUAL` unlink rule checks in `LessonProgressService.ruleReferences`, rewrote `lessonApi.ts` adapter (`List<LessonSummaryView>`, `PATCH .../{publish\|unpublish\|archive\|restore}`, `POST .../complete` + `GET`, `AUTO_ITEMS` <-> `CONDITION_BASED`, two-step upload + Blob download, `ApiResponse<PageResponse<StudentProgressView>>` unwrap, `LessonProgress.tsx` detail invalidation), aligned `lessonsDemoPlugin.ts`, `lessons.test.mjs`, and `e2e/lessons.spec.ts`, and verified `e2e/lessons-live.spec.ts` against Spring Boot + PostgreSQL 16. | `473976f`, `be9f834`, `ba07895`, `69fec1c`, `3b5b97d` |
| **`[HIGH-1]` Inconsistent Source Switching (`useApiSource()` & Demo Modes)** | High | Added pure `resolveDemoFlags` in `frontend/src/services/apiSource.ts` and `useIsDemoMode()` / `useIsPrototypeDemo()` in `frontend/src/components/BackendStatusBanner.tsx`; unified source guards across `LessonWorkspacePage.tsx`, `LessonAttachments.tsx`, `LessonProgress.tsx`, `DashboardLessonLinks.tsx`, `NotificationsPage.tsx`, `ClassroomSourceRoute.tsx`, `DashboardPage.tsx`, and `RoleDashboardPage.tsx`; preserved HTTP API mode in `lessons-demo`; appended `isPrototypeDemo` to lesson query keys. | `c7383e4` |
| **`[HIGH-2]` Dashboard Copy Consistency & Scope Clarification** | High | Replaced remaining Vietnamese empty/fallback paragraphs in `DashboardPage.tsx`, `RoleDashboardPage.tsx`, and `PracticePulse.tsx` with English copy (`0` Vietnamese strings remaining in `frontend/src/features/dashboard/`); preserved E2E-asserted strings in `BackendStatusBanner.tsx` (`"Backend chưa chạy..."`) and `BackendClassroomPage.tsx` (`"Chấp nhận"`, `"Thu hồi"`); prepared accurate PR #50 scope/verification update. | `545ce9a`, `21bc454` |
| **`[MEDIUM-1]` Backend Classroom Pessimistic Write Locks** | Medium | Standardized `classroomService.lockWritable(classroomId)` (`PESSIMISTIC_WRITE`) on classroom-scoped write mutations in `LessonService`, `AnnouncementService`, and `AssignmentService` (sorted `{classroomId, targetClassroomId}` lock acquisition before `requireOwner` in `cloneAssignment`), retaining `lockReadable` on student-facing completion/attempt/recompute reads. | `473976f` |
| **`[MEDIUM-2]` Duplicate Frontend Lockfile** | Medium | Removed `frontend/pnpm-lock.yaml`, leaving `frontend/package-lock.json` (`npm ci` in CI) as the single frontend lockfile; verified `npm ci --dry-run`. | `545ce9a` |

---

## 2. Commit Log (`fcbcdda..21bc454`)

| Commit | Description |
| :--- | :--- |
| `473976f` | `fix(backend): enforce lockWritable on classroom mutations and add lesson material removal & capability endpoints` |
| `be9f834` | `fix(backend): gate student LessonDetailView fields and add authorization tests` |
| `ba07895` | `fix(frontend): align lessonApi adapter and tests with backend LessonController contract` |
| `69fec1c` | `fix(backend): allow unlinking items when lesson completion mode is switched away from AUTO_ITEMS` |
| `3b5b97d` | `fix(frontend): unwrap double-wrapped progress response and harden lessonApi` |
| `c7383e4` | `fix(frontend): unify demo and offline mock source switching across workspace` |
| `545ce9a` | `chore(frontend): unify English dashboard copy and remove duplicate pnpm-lock.yaml` |
| `21bc454` | `fix(frontend): translate remaining dashboard fallback copy to English` |

---

## 3. Executed Verification Suites & Observed Outputs

### 3.1 Backend Verification (Code State Unchanged Since `69fec1c`)

1. **Flyway Default Track (`classpath:db/migration`) — Clean Verify & JAR Build**
   - Command:
     ```bash
     SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
       python3 -B backend/scripts/run-postgres-tests.py -Dspring.test.context.cache.maxSize=8 clean verify
     ```
   - Result: **558 tests run, 0 failures, 0 errors, 3 skipped** (`BUILD SUCCESS`, `03:09 min`), packaged `backend/target/platform-0.0.1-SNAPSHOT.jar`.
   - Skipped (opt-in system properties absent):
     - `com.algolearner.platform.auth.LiveSupabaseFlowTest#registrationSessionsProfileAndInvitationPersistThroughRealApi` (`live.supabase.check`)
     - `com.algolearner.platform.auth.RegistrationBenchmarkTest#measureRegistrationStagesWithMockSmtp` (`registration.benchmark`)
     - `com.algolearner.platform.common.SupabaseConnectionTest#selectOne` (`supabase.check`)

2. **Flyway Alternate Auth Track (`classpath:db/main-auth`)**
   - Command:
     ```bash
     FLYWAY_LOCATIONS=classpath:db/main-auth SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0 SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=4 \
       python3 -B backend/scripts/run-postgres-tests.py -Djacoco.skip=true -Dspring.test.context.cache.maxSize=8 verify
     ```
   - Result: **558 tests run, 0 failures, 0 errors, 3 skipped** (`BUILD SUCCESS`, `03:07 min`).

3. **JaCoCo Report (`backend/target/site/jacoco/jacoco.xml`)**
   - Overall backend: `36420/41381` instructions (`88.0%`), `2710/3858` branches (`70.2%`), `4713/5228` lines (`90.1%`).
   - `com.algolearner.platform.lesson`: `440/464` lines (`94.8%`), `199/276` branches (`72.1%`).

### 3.2 Frontend Verification (Executed on Final Commit `21bc454`)

1. **Unit & Contract Tests (`npm --prefix frontend test`)**
   - Result: **43 passed, 0 failed, 0 skipped** (`node --test tests/*.test.mjs`).
2. **Strict Typecheck (`npm --prefix frontend run typecheck`)**
   - Command: `tsc -b --pretty false && tsc -p tsconfig.lessons-e2e.json --pretty false`
   - Result: **exit 0** (0 errors).
3. **Production Build & Lockfile Consistency (`npm --prefix frontend run build` & `npm --prefix frontend ci --dry-run`)**
   - Vite build exited **0** in `4.46s` (`LessonWorkspacePage-C9mZasC6.js` `404.51 kB`, `index-DYRW6Zh0.js` `777.07 kB`; existing chunk-size notice unchanged).
   - `npm ci --dry-run` exited **0**.
4. **Default Playwright E2E Suite (`npm --prefix frontend run test:e2e`)**
   - Result: **59 passed, 3 skipped, 0 failed** (`2.3m`).
   - Skipped (opt-in specs verified separately below): `e2e/lessons-demo.spec.ts`, `e2e/lessons-live.spec.ts`, `e2e/live-backend.spec.ts`.
5. **Opt-In Lesson Demo E2E (`e2e/lessons-demo.spec.ts`)**
   - Executed against `npm run dev:lessons-demo -- --host 127.0.0.1 --port 3102 --strictPort` with `ALGOLEARN_API_TARGET=http://127.0.0.1:19999` and `ALGOLEARN_LESSON_DEMO_URL=http://127.0.0.1:3102`.
   - Result: **1 passed, 0 failed** (`13.2s`).
6. **Opt-In Live Spring Boot + PostgreSQL 16 E2E (`e2e/lessons-live.spec.ts`, `e2e/live-backend.spec.ts`)**
   - Executed against `backend/target/platform-0.0.1-SNAPSHOT.jar` (`SPRING_PROFILES_ACTIVE=internal-auth,test`) on `127.0.0.1:18080`, `backend/scripts/postman/local_smtp.py` on `127.0.0.1:1025`/`1026`, and a fresh isolated PostgreSQL 16 schema, with live registered+OTP-verified teacher and student accounts (`ALGOLEARN_LIVE_LESSONS=1`, `ALGOLEARN_LIVE_BACKEND=1`).
   - Result: **2 passed, 0 failed** (`23.8s`: `e2e/lessons-live.spec.ts` `14.7s`, `e2e/live-backend.spec.ts` `5.3s`).

### 3.3 QA Subagent Review & Working-Tree Checks

- Whole-branch QA audit (`qa_reviewer`) over `fcbcdda..21bc454`: **Spec Compliance: PASS**, **Code Quality: PASS**.
- Dashboard Vietnamese text scan across `frontend/src/features/dashboard/**/*.{ts,tsx}`: **0 matches**.
- `git diff --check fcbcdda..HEAD`: **exit 0** (clean whitespace/conflict markers).

---

## 4. Scope & Remaining External Acceptance Boundaries

- **Frontend Coverage in PR #50:** Covers **SRS 4.1, 4.2, 4.3, and 4.5** UI integration with the real backend (`4.4` Announcements and `4.6` Assignments/Grading remain backend-complete in PR #50 with frontend integration tracked in `#11` and `#15`).
- **External Acceptance (`#55`):** Live E2E used local Spring Boot `internal-auth,test` + PostgreSQL 16 + mock SMTP fixture; real Supabase Staging DB/Object Storage, ClamAV daemon, Docker Judge worker, and Gmail SMTP remain tracked in `#55`.
