# Public Problem Gallery review

## Implementation

- Public Catalog now opens with a selected problem preview, a technical sample panel, and a horizontal problem filmstrip. Index View uses the same search, status, sort, selection, and pagination state.
- The complete statement opens in a reading dialog. Gallery and Index solve actions keep catalog URL state in the workspace return link.
- The list endpoint has no search parameters. The gallery requests summary pages in batches of four and searches those titles locally; it requests full statements only for the selected problem.
- Classroom Practice and the Coding Workspace retain their existing routes and contracts.

## Verification

- `npm run typecheck`: passed.
- `npm run build`: passed; Vite reported the existing large chunk warning.
- Playwright: `e2e/problem-gallery.spec.ts` and `e2e/student-practice.spec.ts` passed 2/2 with development fixtures.
- The frontend package does not define a lint script.

## Screenshots

- [Before](problem-gallery-before.png)
- [After desktop](problem-gallery-after.png)
- [After mobile](problem-gallery-mobile-after.png)

## Integration limits

- The linked Google Doc SRS could not be opened by the available browser tool. The repository SRS and current API contracts were used.
- Public list summaries contain ID, title, status, and creation time only. They do not contain languages, completion state, or format. A catalog-wide language or completed filter would require additional backend contract support or fetching every complete problem and submission history. The gallery therefore exposes only honest title search, local draft status, and title/newest sorting.
- These checks used development fixtures. Live backend search, judge behavior, and production data were not verified.
