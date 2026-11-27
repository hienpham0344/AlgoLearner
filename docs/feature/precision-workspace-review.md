# AlgoLearn Precision Workspace review

## Scope and decisions

- Redesigned the Student Practice Coding Workspace and its problem preview. Existing RUN, SUBMIT, history, draft storage, Monaco integration, and backend contracts remain in place.
- The low contrast came from workspace color variables being declared only on `.practice-page`; the workspace uses `.practice-focus-shell`. Both now receive explicit surface and foreground tokens.
- Classroom RUN supports custom standard input. Public RUN accepts only language, source, and request key, so the public workspace does not offer custom input.
- RUN is unavailable when no executable sample or supported custom input exists; SUBMIT remains independent of that empty state.
- Return navigation uses the classroom route or Public Catalog route as a direct URL fallback and accepts only matching source routes from the `from` query parameter.
- The existing `marked` and DOMPurify pipeline is retained for other modules. Practice statements use `marked`, KaTeX, and DOMPurify for inline and display math, code fences, escaped dollar signs, and safe HTML.

## Evidence

- Local SRS: `docs/SRS ver 1.1.docx`, sections 4.8, 4.9, and 4.12. FR-PRC-001 AC-04 through AC-07 require repeatable RUN and SUBMIT with separate submission behavior; FR-JDG-001 covers Java and Python.
- Browser test: `frontend/e2e/student-practice.spec.ts` checks math, safe Markdown, navigation after reload and direct entry, Focus and Split, RUN/SUBMIT, history, axe serious/critical findings, and horizontal overflow at 1920×1080, 1440×900, 1280×720, 1024×768, 768×1024, and 390×844.
- Frontend unit tests: `frontend/tests/practice.test.mjs` and `frontend/tests/workspace-nav.test.mjs`.
- Final checks: `npm run typecheck` passed; `node --test tests/practice.test.mjs tests/workspace-nav.test.mjs` passed 6/6; `npx playwright test e2e/student-practice.spec.ts` passed 1/1; `npm run build` passed with Vite's existing large-chunk warning.

## Screenshots

| View | Before | After |
| --- | --- | --- |
| Desktop | [Before](practice-workspace-before.png) | [After](practice-workspace-after.png) |
| Mobile | [Before](practice-workspace-mobile-before.png) | [After](practice-workspace-mobile-after.png) |

Screenshots were captured with development fixtures and display the existing backend status banner. They do not demonstrate a live judge integration.

## Verification gaps

- The linked Google Doc SRS was inaccessible through the available browser tool. The repository copy was used for requirements verification.
- No live backend or sandbox execution was available in this pass; RUN and SUBMIT were verified against the existing fixture and mocked API responses.
- The public submission history API returns metadata without historical source code. The UI does not present invented code for those submissions.
- The repository SRS grants Public Practice to Student and Teacher, while this pre-existing Student Coding Workspace route is student-only. Teacher workspace access was outside this visual redesign.
- The frontend package has no `lint` script. Typecheck, targeted unit tests, browser test, and production build are the available checks.
- The broad `npm test` attempt stalled in unrelated suites and was interrupted; the practice and workspace navigation unit tests completed separately.
