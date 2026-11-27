# Problem Folio review

## Scope

Public Catalog → Read full statement. The Folio keeps the existing public problem API and Gallery URL state. The Coding Workspace link still carries its return path.

## Changes

- Replaced the centered modal with a single 26/74 technical spine and reading canvas. The document scrolls independently; toolbar, navigation, and action footer stay visible.
- Added Previous/Next navigation within the active filtered collection, keyboard Escape and arrow navigation, focus containment and restoration, and scroll reset when switching problems.
- Built the outline from headings actually rendered by Markdown. Short statements without multiple headings omit it.
- Kept Markdown and KaTeX rendering through the existing `PracticeMarkdown` component. Samples come only from the selected problem's detail response; the component checks the response ID before rendering it.
- Corrected the demo fixture: the grid problem now has a grid example in both detail and job result responses. No backend fixture was changed.
- Mobile uses a full screen dialog. Reduced motion users receive no opening animation.

## Visual evidence

- Earlier Gallery baseline: [problem-gallery-before.png](problem-gallery-before.png)
- Folio desktop: [problem-folio-after.png](problem-folio-after.png)
- Folio mobile: [problem-folio-mobile-after.png](problem-folio-mobile-after.png)

## Verification

- `npm.cmd run typecheck` passed.
- `npm.cmd run build` passed.
- `node --test tests/practice.test.mjs` passed (4 tests).
- `npx.cmd playwright test e2e/problem-gallery.spec.ts --config=playwright.folio.config.ts --reporter=line` passed (1 test). The temporary config used the already running demo servers and was removed afterward. It avoids a Windows teardown hang in the standard two-server Playwright configuration.
- E2E checks the grid sample, absence of the interval sample for that problem, navigation between problems, focus restoration, solve route, and desktop/mobile overflow.
- The complete `npm.cmd test` run was stopped after it produced no further output for over 90 seconds; its first eight tests passed, but the whole suite has no final result from this run.

## Limits

The demo data contains short statements only, so the outline and long document behavior were verified from the implementation, not by a long-problem browser fixture. The external Google SRS document was unavailable; local `docs/SRS ver 1.1.docx` and repository guidelines were used.
