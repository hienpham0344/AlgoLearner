# Shared loading primitives

`frontend/src/components/ui/LoadingScreen.tsx` provides a thin indeterminate line for session, route and inline waits. It exposes the loading message with `role="status"` and does not claim a progress percentage or fabricate a content layout.

`Skeleton` renders an `aria-hidden` placeholder inside the real content layout. `LoadingValue` replaces a pending value while keeping its text line height. Controls that depend on pending data remain the caller's responsibility.

The stylesheet is imported once through `frontend/src/index.css` and respects `prefers-reduced-motion`. Use a content-specific component when a layout skeleton is available.

Check the primitives with `node --test tests/loading-primitives.test.mjs` from `frontend`.
