# Open Textbook review

## Scope

Classroom Practice → Classroom Problem Library. The application shell, classroom authorization API, Coding Workspace, and public Gallery remain in place.

## Implementation

- Replaced the administrative problem table and side inspector with one workbench: a dark problem index and a light reading canvas. Each pane scrolls independently, while the reading toolbar and Start solving footer remain visible.
- Moved All classrooms above the classroom title and kept its deterministic `/practice` destination.
- Combined search, format, language, sorting, and reset controls into one toolbar. Format and language options come from the currently loaded API summaries. Status filters sit in the index header.
- Selection uses the `selected` URL parameter. Search, filters, sort, page, and selected problem flow through the Coding Workspace return URL. The reader checks detail IDs before displaying statements or samples and resets its scroll when selection changes.
- Reused the existing Problem Folio for full classroom statements and samples. Its classroom metadata comes from the class detail response.
- On mobile, the index is the initial view; selecting a problem opens a full screen reader. Back to problems returns to the same filtered index.

## Evidence

- Previous library: [practice-library-before.png](practice-library-before.png)
- Desktop: [open-textbook-after.png](open-textbook-after.png)
- Mobile index: [open-textbook-mobile-index-after.png](open-textbook-mobile-index-after.png)
- Mobile reader: [open-textbook-mobile-after.png](open-textbook-mobile-after.png)
- Full statement: [open-textbook-folio-after.png](open-textbook-folio-after.png)

## Verification

- `npm.cmd run typecheck` passed.
- `node --test tests/practice.test.mjs` passed (4 tests).
- `npx.cmd playwright test e2e/student-practice.spec.ts --config=playwright.textbook.config.ts --reporter=line` passed (1 complete end-to-end scenario). The temporary config used running demo servers and was removed after the run because the standard Windows two-server Playwright teardown hangs. The scenario covers selection, Standard I/O and Function / Method data, filters, Folio, mobile index and reader, Coding Workspace, return state, and accessibility checks.
- `npm.cmd run build` passed after the final changes.

## Verification gaps

The external Google SRS URL could not be opened. Local `docs/SRS ver 1.1.docx`, section 4.8, and repository rules were used. Browser tests used intercepted API fixtures; no live backend authorization or data was claimed.
