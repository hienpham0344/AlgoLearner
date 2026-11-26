# AlgoLearn Generative Pattern System (AGPS)

## Implementation

- A registry covers 17 classroom domains. Eight bounded geometry primitives generate tree, graph, architecture, matrix, network, timeline, geometry, and modular compositions. Related domains share a primitive with domain-specific structure.
- FNV-1a hashes the classroom ID; Mulberry32 produces repeatable variants. Subject/category are used if supplied, followed by conservative English/Vietnamese title and description matching. Unrecognized subjects use universal geometry.
- The surface is derived from ID parity, so sorting or filtering does not change a classroom's dark/light treatment. The two existing demo classrooms retain a dark Tree and light Architecture identity.
- SVGs use a 520 × 170 coordinate system, bounded element counts, unique grid IDs, decorative `aria-hidden`, and CSS hover/focus effects. Reduced motion removes transitions.
- Classroom Atelier now renders `PatternRenderer` for arbitrary classroom records. Its navigation, enrollment count, loading, error, and pagination behavior are unchanged. The grid adapts to one or many entries by available width.
- Development-only Pattern Lab: `/dev/pattern-lab`. It exposes family, seed, surface, size, density, complexity, motion, variant, and dataset-size controls. Production builds omit its route and JS chunk.

## Previews

- Previous Atelier: [classroom-atelier-before.png](classroom-atelier-before.png)
- AGPS Atelier desktop: [agps-atelier-after.png](agps-atelier-after.png)
- AGPS Atelier mobile: [agps-atelier-mobile-after.png](agps-atelier-mobile-after.png)
- Pattern Lab, relational matrix: [agps-pattern-lab.png](agps-pattern-lab.png)

## Verification

- `node --test tests/generative-patterns.test.mjs`: 4 tests passed. Covers English/Vietnamese resolution, stable ID identity, all registered families, variants, bounded geometry, and 100 generated classroom patterns.
- `npm.cmd run typecheck`: passed.
- `npx.cmd playwright test e2e/generative-patterns.spec.ts --config=playwright.agps.config.ts --reporter=line`: passed. Temporary no-server config was removed after testing; the existing demo server supplied the page. The browser test checks the lab controls, unique SVG IDs, 2/10/30/100 entry rendering, Atelier identity after reload, reduced motion, mobile overflow, and library navigation.
- Browser dataset updates for 2, 10, 30, and 100 entries took 649 ms and 1194 ms in two demo runs. These are end-to-end UI timings and include automation overhead; they are not standalone SVG benchmark results.
- `npm.cmd run build`: passed. No Pattern Lab text or chunk was found in production JS assets.

## Scope and limits

Patterns are decorative and do not infer academic status or alter classroom data. Different IDs can still yield similar patterns; unique visual identity is not guaranteed. Tests used the local demo and fixture classrooms, not live backend classroom records.
