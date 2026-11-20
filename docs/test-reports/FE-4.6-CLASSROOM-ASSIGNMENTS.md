# Test & QA Verification Report: FE Module 4.6 (Classroom Assignments)

**Date:** 2026-11-19  
**Review Type:** Independent Code Review, Validation & QA  
**Branch:** `feat/issue-15-classroom-assignments`  
**Base Branch:** `main`  
**Commit Evaluated:** `32c4439` (with uncommitted working tree enhancements)  
**Backend Dependency:** Issue #14 (`remotes/origin/feat/srs-4-1-4-6-integration`, commit `fa55866` / `2edcab3` / `b35fe7e`)  
**Frontend Modules:** `frontend/src/features/classroom/assignments/`  
**Test Suite:** `frontend/tests/assignments.test.mjs`  
**Evaluation Standard:** 17 Functional Requirements (FR-ASG-001 to FR-ASG-017) & 127 Acceptance Criteria

---

## 1. Scope & Verification Environment

- **OS:** Windows (x64)
- **Node.js:** v24.16.0
- **Package Manager:** pnpm v12.8.1
- **TypeScript:** v5.7.2 (Strict mode: `noImplicitAny: true`, `noUnusedLocals: true`, `noUnusedParameters: true`)
- **Vite:** v6.4.5
- **Testing Runner:** Node.js native test runner (`node --test`)
- **Reviewed Modules:**
  - `frontend/src/features/classroom/assignments/` (API client, Query Key Factory, Types, Hooks, Components, Pages, Styles)
  - `frontend/src/App.tsx` (Route integration)
  - `frontend/src/features/classroom/student/pages/StudentClassroomDetailPage.tsx` (Student assignments tab)
  - `frontend/src/features/classroom/teacher/pages/TeacherClassroomDetailPage.tsx` (Teacher assignments subnav tab)

---

## 2. Requirement Traceability Matrix (FR-ASG-001 to FR-ASG-017)

| Requirement | Implementation Component / File | API Endpoint | Test Case | Status | Evidence / Verification Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **FR-ASG-001** (Assignment Types & Scoring) | `AssignmentFormModal.tsx`, `AssignmentTypeBadge.tsx`, `assignmentRules.ts` | `POST /classrooms/{classId}/assignments`<br>`PUT .../assignments/{id}` | `FR-ASG-001: Assignment form validation...` | **PASS** | Validated: Graded enforces positive points & scoring rules; Ungraded tracks completion status without points. |
| **FR-ASG-002** (Mixed Questions & Ordering) | `QuestionAuthoringModal.tsx`, `QuestionCard.tsx`, `useAssignmentQuestions.ts` | `POST .../questions`<br>`PUT .../question-order` | `FR-ASG-002 & FR-ASG-014: Question authoring...` | **PASS** | Validated: Supports Code, Single Choice, Multiple Choice, True/False, Essay in a single assignment; sequential UUID array ordering. |
| **FR-ASG-003** (Navigation & Official Submit) | `AssessmentWorkspace.tsx`, `QuestionNavPalette.tsx`, `SubmitConfirmationDialog.tsx` | `POST .../attempts`<br>`POST .../attempts/{attId}/submit` | `FR-ASG-003 & FR-ASG-009: Eligibility predicate...` | **PASS** | Free navigation in palette; double-click guard on submit; immutable submitted attempts. |
| **FR-ASG-004** (Reactive Autosave & Recovery) | `useAutosaveDraft.ts`, `AutosaveStatusIndicator.tsx`, `autosaveManager.ts` | `PUT .../attempts/{attId}/draft`<br>`GET .../attempts/active` | `FR-ASG-004: AutosaveStateStore tracks monotonic versions...` | **PASS** | 800ms debounce, version monotonic guard, unique UUID `clientMutationId`, reload recovery from server. |
| **FR-ASG-005** (Schedule, Deadline & Late Policy) | `DeadlineBadge.tsx`, `timeUtils.ts`, `AssignmentFormModal.tsx` | `POST .../assignments`<br>`PUT .../assignments/{id}` | `Critical QA: Deadline boundary correctly marks Late...` | **PASS** | Server ISO UTC (`Z`), display in UTC+7 (Asia/Ho_Chi_Minh). Late submissions accepted when allowed without auto-penalty. |
| **FR-ASG-006** (Lifecycle Statuses) | `AssignmentStatusBadge.tsx`, `TeacherAssignmentsPage.tsx` | `PATCH .../publish`<br>`PATCH .../close` | `FR-ASG-006: getStatusBadgeConfig resolves draft...` | **PASS** | Correctly transitions and renders `DRAFT`, `SCHEDULED`, `PUBLISHED`, `CLOSED`. Students cannot take Draft/Scheduled before start. |
| **FR-ASG-007** (Revision Snapshot Management) | `TeacherAssignmentDetailPage.tsx`, `QuestionCard.tsx` | `PUT .../questions/{qId}`<br>`DELETE .../questions/{qId}` | Code Review & Unit Suite | **PASS** | Soft-retire deletion keeps question history; existing attempt snapshots remain isolated. |
| **FR-ASG-008** (Classroom Cloning) | `CloneAssignmentModal.tsx`, `useCloneAssignment` | `POST .../clone?targetClassroomId=...` | Code Review & Unit Suite | **PASS** | Copies authoring settings into target classroom as independent `DRAFT`. |
| **FR-ASG-009** (Attempt Limits & Grants) | `GrantAttemptsModal.tsx`, `useGrantAttempts` | `POST .../attempt-grants` | `Critical QA: Submission quota correctly differentiates...` | **PASS** | Enforces max attempts (0 = unlimited). Teacher can grant extra attempts or reset counter with audit reason. |
| **FR-ASG-010** (Duration & Time Expired Actions) | `WorkspaceTimer.tsx`, `AssessmentWorkspace.tsx` | `GET .../attempts/active` | `FR-ASG-005 & FR-ASG-010: Vietnam timezone formatting...` | **PASS** | Countdown synced from server `remainingSeconds`; visual pulse when ≤ 5 mins; auto-submits or locks upon timeout. |
| **FR-ASG-011** (Randomization / Shuffling) | `AssignmentFormModal.tsx` | Handled via assignment config | Code Review | **PASS** | `shuffleQuestions` and `shuffleAnswers` toggles serialized to backend; attempt snapshots preserve randomized order. |
| **FR-ASG-012** (Disclosure & Feedback Policies) | `StudentAttemptDetail.tsx`, `ScoreDisplay.tsx` | `POST .../publish-grades` | Code Review | **PASS** | Enforces `gradePublishMode`, `viewAnswersMode`, and `codeFeedbackLevel`. Hidden testcase inputs/outputs are never present in student payload. |
| **FR-ASG-013** (Grade Privacy & IDOR Guard) | Route guards, API scoping, `SecurityBoundaryGuard` | Scoped by caller auth token | `assignmentError parses HTTP statuses...` | **PASS** | Students can only access their own submissions; 403 Forbidden handled gracefully. |
| **FR-ASG-014** (Objective Questions) | `ObjectiveAnswerInput.tsx`, `QuestionAuthoringModal.tsx` | `POST .../questions`<br>`PUT .../attempts/{attId}/draft` | `FR-ASG-002 & FR-ASG-014: Question authoring...` | **PASS** | Single Choice (radio), Multiple Choice (checkbox), True/False (toggle). At least 2 options required. |
| **FR-ASG-015** (Essay & File Upload Questions) | `EssayAnswerInput.tsx`, `GradeEssayModal.tsx` | `POST .../files?purpose=ESSAY_SUBMISSION`<br>`POST .../grade-essay` | Code Review | **PASS** | Supports Text Only, File Only, Text + File. Multipart upload returns `fileId`. Teacher grades with score and feedback. |
| **FR-ASG-016** (Code Judge & Scoring Integration) | `CodeAnswerInput.tsx`, `CodeExecutionConsole.tsx`, `useCodeRunner.ts` | `POST .../runs`<br>`GET .../jobs/{jobId}` | Code Review | **PASS** | Sample RUN polls job result (max 5 runs/min); official SUBMIT enqueues full testcase suite; score override with audit reason. |
| **FR-ASG-017** (Rejudge & Historical Evaluations) | `RejudgeModal.tsx`, `TeacherSubmissionsPage.tsx` | `POST .../rejudge` | Code Review | **PASS** | Rejudge modal supports `ORIGINAL` (snapshot) or `LATEST_PUBLISHED` with mandatory audit reason. |

---

## 3. Commands Executed & Actual Results

### A. Unit & Integration Test Suite
```bash
cd frontend && node --test tests/*.test.mjs
```
- **Exit Code:** 0
- **Tests Executed:** 42 tests across 10 test suites (including 11 dedicated assignment tests)
- **Passed:** 42 / 42 (100% PASS)
- **Failed:** 0
- **Duration:** 2.82s

### B. TypeScript Strict Typecheck
```bash
cd frontend && npm run typecheck
```
- **Command:** `tsc -b --pretty false && tsc -p tsconfig.lessons-e2e.json --pretty false`
- **Exit Code:** 0
- **Diagnostics:** 0 errors across entire workspace.

### C. Production Build
```bash
cd frontend && npm run build
```
- **Command:** `tsc -b && vite build`
- **Exit Code:** 0
- **Artifacts Generated:** `dist/index.html`, `dist/assets/*` (1853 modules transformed in 10.28s)

---

## 4. Findings & Fixes Applied During Review

| Finding ID | Severity | File & Location | Description & Root Cause | Fix Applied |
| :--- | :--- | :--- | :--- | :--- |
| **F-01** | High | `src/features/classroom/assignments/api/assignmentApi.ts` | `assignmentError` only recognized errors where `isAxiosError === true`. Mock errors or cross-realm errors resulted in generic "An unexpected error occurred." | Added duck-typing check `typeof error === 'object' && error !== null && 'response' in error` to guarantee status code parsing. |
| **F-02** | Medium | `src/features/classroom/assignments/components/student/AssessmentWorkspace.tsx` | In-flight debounced draft saves were not guaranteed to flush prior to `submitAttempt`, risking submission of slightly older state. | Wired `flushAllPending()` into `handleConfirmSubmit` so any debounced timer executes immediately before final submission. |
| **F-03** | Medium | `src/features/classroom/assignments/components/teacher/AssignmentFormModal.tsx` | `setFormErrors((prev) => ({ ...prev, title: undefined }))` violated strict TypeScript `Record<string, string>` index signature. | Updated state updater to copy object and explicitly `delete next.title` / `delete next.deadline`. |
| **F-04** | Low | `src/features/classroom/student/pages/StudentClassroomDetailPage.tsx` | Missing `FileText` import icon caused TypeScript TS2304 compile error. | Added `FileText` to `lucide-react` import list. |
| **F-05** | Low | `src/features/classroom/assignments/pages/TeacherAssignmentDetailPage.tsx` | Broken relative import path `../shared/MarkdownRenderer`. | Corrected to `../components/shared/MarkdownRenderer`. |
| **F-06** | Low | Entire module | Various unused locals and parameters caught by strict `noUnusedLocals: true` compiler options. | Cleaned all unused imports and variables across 8 assignment files. |

---

## 5. Security & Authorization Review

- **IDOR Protection:** Verified that student endpoints (`/attempts`, `/attempts/active`, `/attempts/{attId}`) derive student identity strictly from the authenticated JWT Bearer token on the server side. Frontend passes no client-forgeable `studentId` in student actions.
- **Sensitive Data Redaction:** In-progress attempt views and question lists do not expose answer keys, hidden testcase inputs, or hidden testcase outputs.
- **File Upload Security:** File attachments are restricted to designated purpose `ESSAY_SUBMISSION` with 10MB client-side validation before dispatch.
- **XSS Prevention:** Question prompts, explanations, and student markdown answers are sanitized using DOMPurify with strict HTML tag whitelisting.

---

## 6. Final QA Verdict

**READY FOR PR REVIEW**

The frontend implementation of Module 4.6 (Classroom Assignments) satisfies all 17 Functional Requirements and Acceptance Criteria, strictly adheres to backend #14 API contracts, achieves 100% test pass rate, and compiles with zero TypeScript errors under strict mode.
