# PR #50 Review Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve all Blocker, High, and Medium review findings on PR #50 (`feat/srs-4-1-4-6-integration`) so SRS 4.5 Lesson UI integrates end-to-end with the Spring Boot backend, offline/demo source switching is unified, backend classroom mutations use pessimistic write locks, and all unit/Playwright/live-lesson E2E gates pass.

**Architecture:** We align the frontend `lessonApi.ts` adapter and supporting backend `LessonController`/`LessonService`/`LessonDtos` endpoints with the implemented SRS 4.5 contract (`List<LessonSummaryView>`, `PATCH .../{publish|unpublish|archive|restore}`, `POST .../complete`, two-step `FileController` + `POST/DELETE .../materials`, `AUTO_ITEMS` completion rules, and classroom lesson capabilities/activities). We centralize demo/offline detection in a reactive `useIsPrototypeDemo()` hook backed by `useApiSource()`, enforce `classroomService.lockWritable(classroomId)` across all backend classroom mutations, unify English dashboard copy, and remove the duplicate `pnpm-lock.yaml`.

**Tech Stack:** Java 21, Spring Boot 3.x, Spring Data JPA, JUnit 5 / Mockito, React 19, TypeScript 5.7, TanStack Query v5, Axios, Playwright, Node.js test runner.

**Spec:**
- PR #50 Review (`quackii00`, review ID `5963315338`): https://github.com/hienpham0344/AlgoLearner/pull/50#pullrequestreview-5963315338
- Backend SRS 4.5 API Doc: `docs/api/API-4.5-lessons.md`
- Project Guidelines: `AGENTS.md`, `docs/rules/API_DESIGN_GUIDELINES.md`, `docs/rules/CODING_STANDARDS.md`

---

## Skills, Tools & Subagents Mapping per Task

| Task | Subagent Role (`.agents/agents.toml`) | Primary & Supporting Skills (`.agents/skills/`) | MCP, Native Tools & CLI Utilities |
| :--- | :--- | :--- | :--- |
| **Pre-Flight / Cross-Cutting** | `system_coordinator` | `receiving-code-review`, `writing-plans`, `token-optimization-tricks` | `read_file` (`StartLine`/`EndLine`), `edit_file`, `call_mcp_tool` (`context-mode`: `ctx_execute`, `ctx_search`), `gh api` + `jq` |
| **Task 1:** Backend `lockWritable` & Lesson Material/DTO Endpoints | `java_pro` (`define_subagent` + `invoke_subagent`) | **Primary:** `test-driven-development`<br>**Supporting:** `api-and-interface-design`, `error-handling-patterns`, `debugging-strategies`, `security-and-hardening` | `read_file` (precision slicing), `edit_file` (surgical replacement), `call_mcp_tool` (`context-mode:ctx_execute` running `mvn -f backend/pom.xml test -Dtest=...` and filtering pass/fail summary) |
| **Task 2:** Frontend `lessonApi.ts` Adapter & Contract Alignment (`[BLOCKER-1]`) | `frontend_developer` (`define_subagent` + `invoke_subagent`) | **Primary:** `test-driven-development`<br>**Supporting:** `api-and-interface-design`, `tanstack-query-v5-caching`, `typescript-advanced-type-gymnastics`, `playwright-e2e-automation` | `read_file`, `edit_file`, `call_mcp_tool` (`context-mode:ctx_execute` for `npm --prefix frontend test`, `npm --prefix frontend run typecheck`, `npx --prefix frontend playwright test e2e/lessons.spec.ts`) |
| **Task 3:** Unified `useIsPrototypeDemo()` Source Switching (`[HIGH-1]`) | `frontend_developer` (`define_subagent` + `invoke_subagent`) | **Primary:** `tanstack-query-v5-caching`<br>**Supporting:** `test-driven-development`, `incremental-implementation`, `code-simplification`, `playwright-e2e-automation` | `read_file`, `edit_file`, `call_mcp_tool` (`context-mode:ctx_execute` for `npx --prefix frontend playwright test e2e/backend-source.spec.ts e2e/flows.spec.ts`) |
| **Task 4:** Dashboard English Copy, Lockfile Cleanup (`[HIGH-2]` & Medium #2) | `frontend_developer` + `devops_engineer` | **Primary:** `ui-styling`<br>**Supporting:** `web-accessibility-wcag-aria`, `ci-cd-and-automation`, `git-guardrails` | `read_file`, `edit_file`, `run_command` (`git rm frontend/pnpm-lock.yaml`), `call_mcp_tool` (`context-mode:ctx_execute` for `npm --prefix frontend run build`) |
| **Task 5:** Live Backend E2E (`ALGOLEARN_LIVE_LESSONS=1`), Full QA & PR Update | `qa_reviewer` (`define_subagent` + `invoke_subagent`) | **Primary:** `verification-before-completion`<br>**Supporting:** `playwright-e2e-automation`, `webapp-testing`, `code-review-excellence`, `github-pr-and-collaboration`, `requesting-code-review` | `.agents/skills/webapp-testing/scripts/with_server.py` (multi-server lifecycle: Spring Boot 8080 + Vite 3101), `call_mcp_tool` (`context-mode:ctx_execute` for full `mvn test` & Playwright suites), `gh api` (PATCH PR #50 & re-request review) |

---

## Global Constraints

- **API Envelope & Timezone:** All JSON endpoints under `/api/v1` use `ApiResponse<T>` / `PageResponse<T>` with ISO-8601 UTC timestamps (`Z`); frontend displays `Asia/Ho_Chi_Minh` (`UTC+7`).
- **JPA & Transactions:** No `@Data` on JPA entities, `FetchType.LAZY` on associations, explicit `@Transactional` vs `@Transactional(readOnly = true)`, and `classroomService.lockWritable(classroomId)` (`PESSIMISTIC_WRITE`) on all classroom-scoped write operations.
- **Strict TypeScript:** Zero `any`; `npm run typecheck` (`tsc -b && tsc -p tsconfig.lessons-e2e.json`) must pass with 0 errors.
- **No Unintended Test Regressions:** Preserve existing Vietnamese strings in `BackendStatusBanner.tsx` (`Backend chưa chạy`) and `BackendClassroomPage.tsx` (`Thu hồi`, `Chấp nhận`) that are explicitly asserted by `e2e/backend-source.spec.ts` and `e2e/account-classroom-integration.spec.ts`.

## Review Focus

1. **Student Manual Completion (`POST .../complete` -> `204 No Content`):** Backend returns `204` without a body, whereas `LessonWorkspacePage.tsx` asserts `saved.progress?.completed === true`; `lessonApi.complete` must follow `POST .../complete` with `lessonApi.get(classId, lessonId)` so server-persisted completion is returned.
2. **Teacher Delete Button Disabling After Student Completion:** `e2e/lessons-live.spec.ts` line 62 asserts `Delete lesson` is disabled after clicking `Refresh progress`; `LessonDetailView` must compute `canDelete` from server state (`ruleVersion == 0 && !completions.existsByLessonId(id) && linkedItems.countByLessonId(id) == 0`) and `LessonProgress.tsx` must invalidate `lessonKeys.detail` on refresh.
3. **Two-Step Private Material Upload & Authenticated Download:** Uploading a lesson file must first `POST /api/v1/classrooms/{classId}/files?purpose=LESSON_MATERIAL` (multipart `file`) and then bind the resulting `READY` `fileId` via `POST /api/v1/classrooms/{classId}/lessons/{lessonId}/materials`; downloading must stream `/api/v1/files/{storedFileId}/content` with Bearer auth.
4. **Conditional Completion (`CONDITION_BASED` <-> `AUTO_ITEMS`):** Backend rejects setting `completionMode = AUTO_ITEMS` on a lesson with `ruleVersion == 0`; `lessonApi.create`/`update` must link items first (`POST .../items`) and then call `PUT .../completion-rule` (which atomically increments `ruleVersion` and sets `AUTO_ITEMS`).
5. **Offline Fallback vs Live Backend Probe on Port 3100 (`auth-demo`):** `useIsPrototypeDemo()` must immediately return `true` for `mock-*` tokens / `source === 'mock'` (preventing `503` errors in `LessonWorkspacePage` and `DashboardLessonLinks`), while immediately returning `false` when `source === 'backend'` or a non-`mock-` token (`backend-access`) is active in `e2e/backend-source.spec.ts`.

---

### Task 1: Backend Classroom Write Locking & Lesson Material/DTO Enhancements

**Subagent & Skills:**
- **Subagent:** `java_pro` (`.agents/agents.toml`)
- **Primary Skill:** [`test-driven-development`](file:///home/hien/Project/AlgoLearner/.agents/skills/test-driven-development/SKILL.md)
- **Supporting Skills:** [`api-and-interface-design`](file:///home/hien/Project/AlgoLearner/.agents/skills/api-and-interface-design/SKILL.md), [`error-handling-patterns`](file:///home/hien/Project/AlgoLearner/.agents/skills/error-handling-patterns/SKILL.md), [`security-and-hardening`](file:///home/hien/Project/AlgoLearner/.agents/skills/security-and-hardening/SKILL.md)
- **Tools:** `read_file` (`StartLine`/`EndLine`), `edit_file`, `call_mcp_tool` (`context-mode:ctx_execute` to run Maven tests and parse summary without dumping raw Maven logs into context)

**Files:**
- Modify: `backend/src/main/java/com/algolearner/platform/lesson/LessonService.java:74-115,188-261,300-311,375-401`
- Modify: `backend/src/main/java/com/algolearner/platform/lesson/LessonController.java:119-145`
- Modify: `backend/src/main/java/com/algolearner/platform/lesson/LessonDtos.java:52-98`
- Modify: `backend/src/main/java/com/algolearner/platform/lesson/LessonMaterialRepository.java:1-10`
- Modify: `backend/src/main/java/com/algolearner/platform/lesson/LessonProgressProvider.java:1-11`
- Modify: `backend/src/main/java/com/algolearner/platform/assignment/AssignmentLessonProgressProvider.java:16-35`
- Modify: `backend/src/main/java/com/algolearner/platform/quiz/QuizLessonProgressProvider.java:13-35`
- Modify: `backend/src/main/java/com/algolearner/platform/practice/ClassPracticeService.java:71-76`
- Modify: `backend/src/main/java/com/algolearner/platform/announcement/AnnouncementService.java:51-135`
- Modify: `backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java:1161-1165`
- Test: `backend/src/test/java/com/algolearner/platform/lesson/LessonServiceTest.java`
- Test: `backend/src/test/java/com/algolearner/platform/lesson/LessonControllerTest.java`
- Test: `backend/src/test/java/com/algolearner/platform/announcement/AnnouncementServiceTest.java`

**Interfaces:**
- Produces:
  - `DELETE /api/v1/classrooms/{classId}/lessons/{lessonId}/materials/{materialId}` (`204 No Content`)
  - `GET /api/v1/classrooms/{classId}/lessons/capabilities` -> `ApiResponse<LessonDtos.CapabilitiesView>`
  - `GET /api/v1/classrooms/{classId}/lessons/activities` -> `ApiResponse<List<LessonDtos.ActivityOptionView>>`
  - Enriched `LessonDtos.LessonDetailView` with `LessonStatus previousStatus`, `Boolean canDelete`, `List<UUID> requiredItemIds`, and `LessonDtos.LinkedItemView` with `String title`, `Boolean available`
  - Consistent `classroomService.lockWritable(classroomId)` on all mutations in `LessonService`, `AnnouncementService`, and `AssignmentService`

- [ ] **Step 1: Write failing unit tests in `LessonServiceTest.java`, `LessonControllerTest.java`, and `AnnouncementServiceTest.java`**

Add tests in `LessonServiceTest.java` verifying:
1. `create`, `update`, `addMaterial`, `removeMaterial`, and `configureCompletionRule` invoke `classroomService.lockWritable(classroom.getId())`.
2. `removeMaterial(teacher.getId(), classroom.getId(), lesson.getId(), materialId)` deletes an existing `LessonMaterialEntity` belonging to `lesson` and throws `FORBIDDEN_OPERATION` if the lesson is `ARCHIVED` or `ResourceNotFoundException` if the material does not belong to `lesson`.
3. `toDetailView` sets `previousStatus` from `archivedFromStatus`, sets `canDelete = false` when `completions.existsByLessonId(lesson.getId())` is `true`, and populates `requiredItemIds` from `progressService.rule(lesson.getId())`.

Update `AnnouncementServiceTest.java` setup to stub `when(classroomService.lockWritable(classroom.getId())).thenReturn(classroom)` and assert `verify(classroomService).lockWritable(classroom.getId())` on `create`, `update`, `delete`, `pin`, `unpin`.

- [ ] **Step 2: Run backend unit tests via `ctx_execute` to verify they fail**

Run via `context-mode:ctx_execute`: `mvn -f backend/pom.xml test -Dtest=LessonServiceTest,LessonControllerTest,AnnouncementServiceTest`
Expected: FAIL (missing `removeMaterial` method and `lockWritable` verification failures).

- [ ] **Step 3: Implement `lockWritable`, `removeMaterial`, capabilities/activities endpoints, and enriched `LessonDetailView`**

1. In `AnnouncementService.java` (`create`, `update`, `delete`, `pin`, `unpin`), `AssignmentService.java` (`requireWritableClassroom`), and `LessonService.java` (`create`, `update`, `addMaterial`, `configureCompletionRule`), replace `classroomService.lockReadable(...)` with `classroomService.lockWritable(...)`.
2. In `LessonMaterialRepository.java`, add `Optional<LessonMaterialEntity> findByIdAndLessonId(UUID id, UUID lessonId);`.
3. In `LessonDtos.java`:
   - Add `CapabilitiesView(boolean uploadsAvailable, boolean activityLinkingAvailable, long maxFileSizeBytes, List<String> allowedExtensions)`.
   - Add `ActivityOptionView(UUID id, LinkedItemType type, String title, UUID linkedLessonId, boolean available)`.
   - Add optional `String title` and `Boolean available` to `LinkedItemView` (keeping a 4-arg convenience constructor `(UUID id, LinkedItemType itemType, UUID itemId, int displayOrder)` delegating with `(id, itemType, itemId, displayOrder, null, true)`).
   - Add `LessonStatus previousStatus`, `Boolean canDelete`, and `List<UUID> requiredItemIds` to `LessonDetailView` (keeping the 16-arg constructor delegating with `(..., null, true, List.of())` so existing tests compile unchanged).
4. In `LessonService.java` and `LessonController.java`:
   - Implement `removeMaterial(UUID accountId, UUID classroomId, UUID lessonId, UUID materialId)` and `@DeleteMapping("/{lessonId}/materials/{materialId}") @ResponseStatus(HttpStatus.NO_CONTENT)`.
   - Implement `capabilities(UUID accountId, UUID classroomId)` (`@GetMapping("/capabilities")`) and `activities(UUID accountId, UUID classroomId)` (`@GetMapping("/activities")`).
   - Populate `previousStatus`, `canDelete`, and `requiredItemIds` in `LessonService.toDetailView`.

- [ ] **Step 4: Run backend tests via `ctx_execute` to verify they pass**

Run via `context-mode:ctx_execute`: `mvn -f backend/pom.xml test -Dtest=LessonServiceTest,LessonControllerTest,LessonApiTest,AnnouncementServiceTest,AssignmentServiceTest`
Expected: PASS (0 failures, 0 errors).

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/java/com/algolearner/platform/lesson/ \
        backend/src/main/java/com/algolearner/platform/announcement/AnnouncementService.java \
        backend/src/main/java/com/algolearner/platform/assignment/AssignmentService.java \
        backend/src/main/java/com/algolearner/platform/assignment/AssignmentLessonProgressProvider.java \
        backend/src/main/java/com/algolearner/platform/quiz/QuizLessonProgressProvider.java \
        backend/src/main/java/com/algolearner/platform/practice/ClassPracticeService.java \
        backend/src/test/java/com/algolearner/platform/lesson/ \
        backend/src/test/java/com/algolearner/platform/announcement/AnnouncementServiceTest.java
git commit -m "fix(backend): enforce lockWritable on classroom mutations and support lesson material removal"
```

---

### Task 2: Frontend `lessonApi.ts` Adapter & Contract Alignment (`[BLOCKER-1]`)

**Subagent & Skills:**
- **Subagent:** `frontend_developer` (`.agents/agents.toml`)
- **Primary Skill:** [`test-driven-development`](file:///home/hien/Project/AlgoLearner/.agents/skills/test-driven-development/SKILL.md)
- **Supporting Skills:** [`api-and-interface-design`](file:///home/hien/Project/AlgoLearner/.agents/skills/api-and-interface-design/SKILL.md), [`tanstack-query-v5-caching`](file:///home/hien/Project/AlgoLearner/.agents/skills/tanstack-query-v5-caching/SKILL.md), [`typescript-advanced-type-gymnastics`](file:///home/hien/Project/AlgoLearner/.agents/skills/typescript-advanced-type-gymnastics/SKILL.md), [`playwright-e2e-automation`](file:///home/hien/Project/AlgoLearner/.agents/skills/playwright-e2e-automation/SKILL.md)
- **Tools:** `read_file`, `edit_file`, `call_mcp_tool` (`context-mode:ctx_execute` for `npm test`, `npm run typecheck`, and Playwright `e2e/lessons.spec.ts`)

**Files:**
- Modify: `frontend/src/features/classroom/lessons/api/lessonApi.ts:1-65`
- Modify: `frontend/src/features/classroom/lessons/components/LessonProgress.tsx:1-26`
- Modify: `frontend/dev/lessonsDemoPlugin.ts:80-158`
- Test: `frontend/tests/lessons.test.mjs`
- Test: `frontend/e2e/lessons.spec.ts`

**Interfaces:**
- Consumes:
  - `GET /api/v1/classrooms/{classId}/lessons` -> `ApiResponse<LessonSummaryView[]>`
  - `POST /api/v1/classrooms/{classId}/lessons` -> `ApiResponse<LessonDetailView>`
  - `GET /api/v1/classrooms/{classId}/lessons/{lessonId}` -> `ApiResponse<LessonDetailView>`
  - `PUT /api/v1/classrooms/{classId}/lessons/{lessonId}` -> `ApiResponse<LessonDetailView>`
  - `PATCH /api/v1/classrooms/{classId}/lessons/{lessonId}/{publish|unpublish|archive|restore}` -> `ApiResponse<LessonDetailView>`
  - `POST /api/v1/classrooms/{classId}/lessons/{lessonId}/complete` -> `204 No Content`
  - `POST /api/v1/classrooms/{classId}/files?purpose=LESSON_MATERIAL` + `POST /api/v1/classrooms/{classId}/lessons/{lessonId}/materials`
  - `DELETE /api/v1/classrooms/{classId}/lessons/{lessonId}/materials/{materialId}` -> `204 No Content`
  - `GET /api/v1/files/{fileId}/content` -> binary `Blob`
  - `POST/DELETE /api/v1/classrooms/{classId}/lessons/{lessonId}/items` & `PUT .../completion-rule`
  - `GET /api/v1/classrooms/{classId}/lessons/capabilities` & `GET /api/v1/classrooms/{classId}/lessons/activities`
- Produces:
  - `lessonApi` methods returning the exact TypeScript domain interfaces (`PaginatedData<Lesson>`, `Lesson`, `LessonAttachment`, `LessonCapabilities`, `LessonActivity[]`, `PaginatedData<StudentLessonProgress>`) consumed by `LessonWorkspacePage.tsx`, `LessonEditor.tsx`, `LessonAttachments.tsx`, and `LessonProgress.tsx`.

- [ ] **Step 1: Update contract unit test in `frontend/tests/lessons.test.mjs` to assert the real Backend `#12` endpoints & payload adapters**

Update the `'LESSON contract'` test in `frontend/tests/lessons.test.mjs` to verify:
1. `lessonApi.list('class-a', 0)` accepts a raw array `LessonSummaryView[]` from `GET /classrooms/class-a/lessons` and normalizes it into `PaginatedData<Lesson>` with `completionMode: 'AUTO_ITEMS'` mapped to `'CONDITION_BASED'` and `isCompleted: true` mapped to `progress: { completed: true, completedAt }`.
2. `lessonApi.create('class-a', input)` serializes `codeExample` and `externalLinks` as JSON strings and syncs linked items / completion rules when present.
3. `lessonApi.transition('class-a', 'lesson-a', 'draft')` sends `PATCH /classrooms/class-a/lessons/lesson-a/unpublish`.
4. `lessonApi.complete('class-a', 'lesson-a')` sends `POST /classrooms/class-a/lessons/lesson-a/complete` (`204`) followed by `GET /classrooms/class-a/lessons/lesson-a` and returns `progress.completed === true`.
5. `lessonApi.upload('class-a', 'lesson-a', file)` uploads multipart to `POST /classrooms/class-a/files?purpose=LESSON_MATERIAL` and attaches `{ fileId }` via `POST /classrooms/class-a/lessons/lesson-a/materials`.
6. `lessonApi.removeAttachment('class-a', 'lesson-a', 'mat-1')` sends `DELETE /classrooms/class-a/lessons/lesson-a/materials/mat-1`.
7. `lessonApi.download('class-a', 'lesson-a', 'mat-1')` downloads from the cached `fileUrl` (`/files/{fileId}/content`).
8. `lessonApi.progress('class-a', 'lesson-a', 0)` maps `StudentProgressView.username` to `StudentLessonProgress.displayName`.

- [ ] **Step 2: Run `npm test` in `frontend/` to verify the updated contract test fails**

Run: `npm --prefix frontend test`
Expected: FAIL in `'LESSON contract'`.

- [ ] **Step 3: Implement the Backend `#12` adapter in `frontend/src/features/classroom/lessons/api/lessonApi.ts` and update `LessonProgress.tsx`, `e2e/lessons.spec.ts`, and `dev/lessonsDemoPlugin.ts`**

1. In `lessonApi.ts`:
   - Implement normalizers `toLesson(raw)`, `parseCodeExample(raw)`, `parseExternalLinks(raw)`, `toAttachment(material)` (caching `materialDownloadPaths.set(material.id, normalizeApiPath(material.fileUrl))`), and `toBackendPayload(input)`.
   - Implement `syncActivitiesAndRule(classId, lessonId, input, currentLesson)` so creating/updating a lesson synchronizes `POST/DELETE .../items` and `PUT .../completion-rule` when `activityIds` or `completionMode === 'CONDITION_BASED'` are used.
   - Update `transition` to use `apiClient.patch` and map `'draft'` -> `'unpublish'`.
   - Update `complete` to `POST .../complete` and then fetch `lessonApi.get(classId, lessonId)` when the response has no `Lesson` body (`204`).
   - Update `upload`, `removeAttachment`, and `download` to use `/classrooms/{classId}/files?purpose=LESSON_MATERIAL`, `/lessons/{lessonId}/materials`, and `/files/{fileId}/content`.
   - Update `capabilities` and `activities` to call `${base(classId)}/capabilities` and `${base(classId)}/activities`.
   - Update `progress` to map `username` -> `displayName`.
2. In `LessonProgress.tsx`:
   - Inject `useQueryClient()` and when `Refresh progress` is clicked (or when `query.data` indicates any student completed), invalidate `lessonKeys.detail(userId, classId, lessonId)` so `lesson.canDelete` updates immediately.
3. Update `frontend/e2e/lessons.spec.ts` and `frontend/dev/lessonsDemoPlugin.ts` route handlers to match the `#12` Backend routes (`PATCH .../{publish|unpublish|archive|restore}`, `POST .../complete`, `POST /classrooms/{classId}/files`, `POST/DELETE .../materials`, `GET /files/{id}/content`, `GET .../lessons/capabilities`, `GET .../lessons/activities`).

- [ ] **Step 4: Run frontend unit tests, typecheck, and Playwright `lessons.spec.ts`**

Run via `context-mode:ctx_execute`: `npm --prefix frontend test && npm --prefix frontend run typecheck && npx --prefix frontend playwright test e2e/lessons.spec.ts`
Expected: PASS (all unit tests and all tests in `e2e/lessons.spec.ts` pass).

- [ ] **Step 5: Commit Task 2**

```bash
git add frontend/src/features/classroom/lessons/api/lessonApi.ts \
        frontend/src/features/classroom/lessons/components/LessonProgress.tsx \
        frontend/dev/lessonsDemoPlugin.ts \
        frontend/tests/lessons.test.mjs \
        frontend/e2e/lessons.spec.ts
git commit -m "fix(frontend): align lessonApi adapter and tests with backend LessonController contract"
```

---

### Task 3: Unified Source Switching (`useIsPrototypeDemo()`) Across Workspace (`[HIGH-1]`)

**Subagent & Skills:**
- **Subagent:** `frontend_developer` (`.agents/agents.toml`)
- **Primary Skill:** [`tanstack-query-v5-caching`](file:///home/hien/Project/AlgoLearner/.agents/skills/tanstack-query-v5-caching/SKILL.md)
- **Supporting Skills:** [`test-driven-development`](file:///home/hien/Project/AlgoLearner/.agents/skills/test-driven-development/SKILL.md), [`incremental-implementation`](file:///home/hien/Project/AlgoLearner/.agents/skills/incremental-implementation/SKILL.md), [`code-simplification`](file:///home/hien/Project/AlgoLearner/.agents/skills/code-simplification/SKILL.md), [`playwright-e2e-automation`](file:///home/hien/Project/AlgoLearner/.agents/skills/playwright-e2e-automation/SKILL.md)
- **Tools:** `read_file`, `edit_file`, `call_mcp_tool` (`context-mode:ctx_execute` running `npx --prefix frontend playwright test e2e/backend-source.spec.ts e2e/flows.spec.ts`)

**Files:**
- Modify: `frontend/src/components/BackendStatusBanner.tsx:1-25`
- Modify: `frontend/src/features/classroom/lessons/pages/LessonWorkspacePage.tsx:28-85,175-186,455-495,562-600`
- Modify: `frontend/src/features/classroom/lessons/components/LessonAttachments.tsx:1-56`
- Modify: `frontend/src/features/classroom/lessons/components/LessonProgress.tsx:1-26`
- Modify: `frontend/src/features/classroom/lessons/components/DashboardLessonLinks.tsx:1-33`
- Modify: `frontend/src/features/dashboard/pages/NotificationsPage.tsx:1-65`
- Modify: `frontend/src/features/classroom/pages/ClassroomSourceRoute.tsx:20-36`
- Modify: `frontend/src/features/dashboard/pages/DashboardPage.tsx:118-125`
- Modify: `frontend/src/features/dashboard/pages/RoleDashboardPage.tsx:808-815`

**Interfaces:**
- Produces:
  - `export function useIsPrototypeDemo(): boolean` in `frontend/src/components/BackendStatusBanner.tsx`:
    ```ts
    export function useIsPrototypeDemo(): boolean {
      const source = useApiSource();
      if (source === 'backend') return false;
      if (source === 'mock') return true;
      const token = typeof window !== 'undefined' ? window.localStorage.getItem('algolearner_token') : null;
      if (token && !token.startsWith('mock-')) return false;
      return ['auth-demo', 'lessons-demo'].includes(import.meta.env.MODE);
    }
    ```

- [ ] **Step 1: Add an offline lesson navigation check in `frontend/e2e/backend-source.spec.ts`**

In `frontend/e2e/backend-source.spec.ts` (`'offline mock supports profile reload and classrooms with a visible notice'`), add an assertion verifying that on `/dashboard` the `Class lessons` section renders offline demo classrooms without an error alert, and navigating to `/student/classrooms/cls-algo-2026/lessons` renders the mock lessons without a `503` error.

- [ ] **Step 2: Run `e2e/backend-source.spec.ts` to verify it fails before the fix**

Run via `context-mode:ctx_execute`: `npx --prefix frontend playwright test e2e/backend-source.spec.ts`
Expected: FAIL (`DashboardLessonLinks` and `LessonWorkspacePage` hit `/api/v1/classrooms` while offline and render a `503` alert).

- [ ] **Step 3: Implement `useIsPrototypeDemo()` and wire it into Lesson, Dashboard, Notification, and Classroom routes**

1. Export `useIsPrototypeDemo()` from `frontend/src/components/BackendStatusBanner.tsx`.
2. In `LessonWorkspacePage.tsx`, `LessonAttachments.tsx`, and `LessonProgress.tsx`, replace static `const IS_PROTOTYPE_DEMO = import.meta.env.MODE === 'auth-demo';` with `const isPrototypeDemo = useIsPrototypeDemo();`.
3. In `DashboardLessonLinks.tsx`, read `const isPrototypeDemo = useIsPrototypeDemo();`. When `isPrototypeDemo` is `true`, resolve classrooms from `readStoredClassrooms()` and `readStoredPersona(role)` (filtering active member classrooms for `STUDENT` and owned classrooms for `TEACHER`) instead of calling `classroomApi.list(page)`.
4. In `NotificationsPage.tsx`, `DashboardPage.tsx`, `RoleDashboardPage.tsx`, and `ClassroomSourceRoute.tsx`, replace the inconsistent `IS_DEMO` / `isDemo` / `forceMock` expressions with `useIsPrototypeDemo()`.

- [ ] **Step 4: Run `e2e/backend-source.spec.ts` and `e2e/flows.spec.ts` to verify all source-switching flows pass**

Run via `context-mode:ctx_execute`: `npx --prefix frontend playwright test e2e/backend-source.spec.ts e2e/flows.spec.ts`
Expected: PASS.

- [ ] **Step 5: Commit Task 3**

```bash
git add frontend/src/components/BackendStatusBanner.tsx \
        frontend/src/features/classroom/lessons/pages/LessonWorkspacePage.tsx \
        frontend/src/features/classroom/lessons/components/LessonAttachments.tsx \
        frontend/src/features/classroom/lessons/components/LessonProgress.tsx \
        frontend/src/features/classroom/lessons/components/DashboardLessonLinks.tsx \
        frontend/src/features/dashboard/pages/NotificationsPage.tsx \
        frontend/src/features/classroom/pages/ClassroomSourceRoute.tsx \
        frontend/src/features/dashboard/pages/DashboardPage.tsx \
        frontend/src/features/dashboard/pages/RoleDashboardPage.tsx \
        frontend/e2e/backend-source.spec.ts
git commit -m "fix(frontend): unify demo and offline mock source switching via useIsPrototypeDemo"
```

---

### Task 4: UI Copy Consistency & Lockfile Cleanup (`[HIGH-2]` & Medium #2)

**Subagent & Skills:**
- **Subagent:** `frontend_developer` / `devops_engineer` (`.agents/agents.toml`)
- **Primary Skill:** [`ui-styling`](file:///home/hien/Project/AlgoLearner/.agents/skills/ui-styling/SKILL.md)
- **Supporting Skills:** [`web-accessibility-wcag-aria`](file:///home/hien/Project/AlgoLearner/.agents/skills/web-accessibility-wcag-aria/SKILL.md), [`ci-cd-and-automation`](file:///home/hien/Project/AlgoLearner/.agents/skills/ci-cd-and-automation/SKILL.md)
- **Tools:** `read_file`, `edit_file`, `run_command` (`git rm frontend/pnpm-lock.yaml`), `call_mcp_tool` (`context-mode:ctx_execute`)

**Files:**
- Modify: `frontend/src/features/dashboard/pages/DashboardPage.tsx:339-347,430-437`
- Modify: `frontend/src/features/dashboard/pages/RoleDashboardPage.tsx:214-220,367-373,418-423,766-771`
- Delete: `frontend/pnpm-lock.yaml`

**Interfaces:**
- Restores unified English empty-state copy across `DashboardPage.tsx` and `RoleDashboardPage.tsx` and leaves `frontend/package-lock.json` as the sole frontend lockfile.

- [ ] **Step 1: Verify mixed Vietnamese strings in `DashboardPage.tsx` and `RoleDashboardPage.tsx` and presence of `frontend/pnpm-lock.yaml`**

Run: `grep -n "Phân hệ\|Khóa học công khai\|Bài tập và quiz\|Dữ liệu khóa học" frontend/src/features/dashboard/pages/*.tsx && test -f frontend/pnpm-lock.yaml`
Expected: Matches on `DashboardPage.tsx` and `RoleDashboardPage.tsx`, and `pnpm-lock.yaml` exists.

- [ ] **Step 2: Restore English empty-state copy in `DashboardPage.tsx` & `RoleDashboardPage.tsx` and remove `frontend/pnpm-lock.yaml`**

1. In `DashboardPage.tsx`:
   - Line 342: `<p>Choose a topic that interests you and start learning at your pace.</p>`
   - Line 432: `<p>Assignments and quizzes from your enrolled classes will appear here in chronological order.</p>`
2. In `RoleDashboardPage.tsx`:
   - Line 217: `<p>Published assignments for your active classrooms will appear here.</p>`
   - Line 370: `<p>Ungraded work and recent submissions will appear here.</p>`
   - Lines 421 & 769: remove `<p>Dữ liệu khóa học, bài luyện tập công khai và duyệt nội dung chưa được kết nối.</p>`.
3. Remove `frontend/pnpm-lock.yaml`: `git rm frontend/pnpm-lock.yaml`.

- [ ] **Step 3: Run frontend build, unit tests, and dashboard E2E tests**

Run via `context-mode:ctx_execute`: `npm --prefix frontend test && npm --prefix frontend run build && npx --prefix frontend playwright test e2e/flows.spec.ts`
Expected: PASS and zero remaining Vietnamese strings in `DashboardPage.tsx` / `RoleDashboardPage.tsx`.

- [ ] **Step 4: Commit Task 4**

```bash
git add frontend/src/features/dashboard/pages/DashboardPage.tsx \
        frontend/src/features/dashboard/pages/RoleDashboardPage.tsx \
        frontend/pnpm-lock.yaml
git commit -m "chore(frontend): unify English dashboard copy and remove duplicate pnpm-lock.yaml"
```

---

### Task 5: End-to-End Live Backend Lesson Verification (`ALGOLEARN_LIVE_LESSONS=1`) & PR Metadata Update

**Subagent & Skills:**
- **Subagent:** `qa_reviewer` (`.agents/agents.toml`)
- **Primary Skill:** [`verification-before-completion`](file:///home/hien/Project/AlgoLearner/.agents/skills/verification-before-completion/SKILL.md)
- **Supporting Skills:** [`playwright-e2e-automation`](file:///home/hien/Project/AlgoLearner/.agents/skills/playwright-e2e-automation/SKILL.md), [`webapp-testing`](file:///home/hien/Project/AlgoLearner/.agents/skills/webapp-testing/SKILL.md), [`code-review-excellence`](file:///home/hien/Project/AlgoLearner/.agents/skills/code-review-excellence/SKILL.md), [`github-pr-and-collaboration`](file:///home/hien/Project/AlgoLearner/.agents/skills/github-pr-and-collaboration/SKILL.md), [`requesting-code-review`](file:///home/hien/Project/AlgoLearner/.agents/skills/requesting-code-review/SKILL.md)
- **Tools:**
  - `.agents/skills/webapp-testing/scripts/with_server.py` (or `run_command` / `ctx_execute` orchestrating PostgreSQL 16 + Spring Boot API on `:8080` + Vite proxy on `:3101`)
  - `call_mcp_tool` (`context-mode:ctx_execute` for bounded verification output of `mvn test`, `npm test`, `npx playwright test`, and `e2e/lessons-live.spec.ts`)
  - `gh api repos/hienpham0344/AlgoLearner/pulls/50 -X PATCH` & `gh api repos/hienpham0344/AlgoLearner/pulls/50/requested_reviewers -X POST`

**Files:**
- Modify: `frontend/e2e/lessons-live.spec.ts:1-70` (if any seed/env helper adjustment is needed)
- Create/Update: `docs/test-reports/2026-11-19-pr-50-review-verification.md`

**Interfaces:**
- Executes `e2e/lessons-live.spec.ts` with `ALGOLEARN_LIVE_LESSONS=1` against the real Spring Boot backend (`LessonController`, `ClassroomController`, `AuthController`) and PostgreSQL 16, runs the full backend and frontend test suites, and updates PR #50 description with accurate scope and test counts.

- [ ] **Step 1: Run full Backend verification (`mvn test`) via `ctx_execute`**

Run via `context-mode:ctx_execute`: `mvn -f backend/pom.xml test`
Expected: PASS with 0 failures and 0 errors.

- [ ] **Step 2: Run full Frontend unit tests, typecheck, build, and Playwright suite via `ctx_execute`**

Run via `context-mode:ctx_execute`: `npm --prefix frontend test && npm --prefix frontend run typecheck && npm --prefix frontend run build && npx --prefix frontend playwright test`
Expected: PASS across all unit tests and Playwright E2E specs.

- [ ] **Step 3: Execute live lesson integration gate (`ALGOLEARN_LIVE_LESSONS=1`) against the running Spring Boot backend**

Start the local Spring Boot backend (connected to PostgreSQL 16) and Vite proxy targeting `http://127.0.0.1:8080` (using `.agents/skills/webapp-testing/scripts/with_server.py` or bounded background tasks), provision verified teacher and student accounts, and run:
Run: `ALGOLEARN_LIVE_LESSONS=1 ALGOLEARN_LESSON_TEACHER=... ALGOLEARN_LESSON_TEACHER_PASSWORD=... ALGOLEARN_LESSON_STUDENT=... ALGOLEARN_LESSON_STUDENT_PASSWORD=... npx --prefix frontend playwright test e2e/lessons-live.spec.ts`
Expected: `1 passed` (`LIVE LES: teacher authoring and lifecycle preserve student completion through the real API`).

- [ ] **Step 4: Record verification evidence and update PR #50 title/body via `gh api`**

Write `docs/test-reports/2026-11-19-pr-50-review-verification.md` summarizing the resolved review items and test outputs, commit it, and update PR #50 via `gh api repos/hienpham0344/AlgoLearner/pulls/50 -X PATCH` to clarify the exact frontend scope (SRS 4.1, 4.2, 4.3, 4.5 integrated with real backend; SRS 4.4 and 4.6 backend-complete with frontend tracked in #11 and #15) and updated unit/Playwright test counts.

- [ ] **Step 5: Commit verification report**

```bash
git add docs/test-reports/2026-11-19-pr-50-review-verification.md
git commit -m "docs(test-report): record PR #50 review resolution and live lesson E2E verification"
```
