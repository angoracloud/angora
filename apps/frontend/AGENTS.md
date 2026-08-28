# AI Agent Guidelines — Frontend

Scoped to `apps/frontend/`. See the [root AGENTS.md](../../AGENTS.md) for repo-wide rules, and [`packages/config/AGENTS.md`](../../packages/config/AGENTS.md) for the shared TypeScript/ESLint/Vite config this app consumes.

- **Framework**: React 19 + TypeScript 7 + Vite 8
- **Build**: pnpm + Node.js 24
- **Serve**: nginx:1.30-alpine on port 3000 — base image is pinned to a specific version (like every other Dockerfile in the repo); don't revert to floating `nginx:alpine`
- **Vite root is `src/`**: `vite.config.ts` sets `root: 'src'` and `build.outDir: '../dist'` because `index.html` lives in `src/`, not the project root. Its script tag references `/main.tsx` (relative to that root), not `/src/main.tsx`. Don't change the root without updating both.
- **TypeScript 7 dropped `baseUrl`**: path aliases use `"paths": {"@/*": ["./src/*"]}` with no `baseUrl`.
- **`tsconfig.json`/`tsconfig.node.json`, `eslint.config.mjs`, and `vite.config.ts` all pull from `@angora/config`** — don't inline rules/compiler options that duplicate what the shared base already sets. `vite.config.ts` imports `@angora/config/vite/base` with **no extension** — `tsconfig.node.json` has `composite: true`, which is incompatible with the `noEmit`/`allowImportingTsExtensions` combo that an explicit `.ts` extension would need.
- **The `@/*` path alias in `tsconfig.json` is not wired into Vite's `resolve.alias`** — it type-checks but 404s at runtime/build. Use relative imports (`../ui/Avatar`) instead; don't add `@/`-prefixed imports without also adding the matching Vite alias.
- **`Dockerfile` builds from the repo root**, not `apps/frontend/` — see [`packages/config/AGENTS.md`](../../packages/config/AGENTS.md) before editing it.
- **Design system**: `src/index.css` holds the only global CSS — a `:root` token block (`--color-*`, `--space-*`, `--radius-*`, `--font-*`, `--shadow-*`) plus a minimal reset/base. Every component in `src/components/ui/` and `src/components/layout/` is a `Name.tsx` + `Name.module.css` pair (CSS Modules — `import styles from './Name.module.css'`, reference via `styles.foo`, never a raw class-name string). Page components compose `components/ui/` primitives instead of writing their own CSS; don't add new global classes to `index.css` or a plain (non-module) `.css` file.
- **Routing** uses `@tanstack/react-router` — a single code-based route tree in `src/router.tsx` (not file-based/codegen). Route paths are centralized in `src/routes.ts`: `ROUTES` holds full absolute paths for `Link`/`navigate`/`redirect`, and `ROUTE_SEGMENTS` holds the bare relative segments `router.tsx`'s `createRoute({ path })` calls need — `ROUTES`'s full paths are template-literal-derived from those same segments, not a second independently-typed copy. **Don't pass a `ROUTES.*` full path as a non-root route's `path`** — a child route's path composes relative to its parent regardless of a leading slash, so a full path there double-prefixes (confirmed: `/discordbot` + `/discordbot/servers` silently resolved to `/discordbot/discordbot/servers`, caught only because it broke the `Link to=` literal-type union everywhere else in the app). Likewise, never derive a route's `path` via a function call — TanStack Router's type safety depends on `path` being a literal string type; a computed value widens to plain `string` and silently degrades every `Link to=`/`useMatches().staticData` type in the app back to `"." | ".." | "/"`. Each leaf route sets `staticData: { title }` (or `{ crumbs }`), read by `TopBar` via `useMatches()` — a typed module augmentation (`StaticDataRouteOption`) lives in `router.tsx`. `App.tsx` renders `<RouterProvider router={router} />`.
- **Data fetching** uses `@tanstack/react-query` — see `src/hooks/discordQueries.ts` for the pattern (a query-keys object + thin `useQuery`/`useMutation` wrapper hooks). Don't hand-roll fetch/poll/focus-refetch logic (`setInterval` + `window.addEventListener('focus', ...)` + `isMounted` guards) — `refetchInterval`/`refetchOnWindowFocus` plus cache dedup across components (same query key = shared request, no prop-drilling needed) replace all of that.
- **React Compiler is enabled** (`babel-plugin-react-compiler`, wired via `@rolldown/plugin-babel` + `reactCompilerPreset()` in `vite.config.ts` — `@vitejs/plugin-react@6` dropped its own internal Babel support, so the old `react({ babel: { plugins: [...] } })` option no longer works). Don't hand-write `useMemo`/`useCallback`/`React.memo` for routine cases — trust the compiler. It runs in the default `compilationMode: 'infer'`, which is intentionally conservative and won't select every component (confirmed: several components in this codebase aren't compiled, and that's fine — the compiler is purely additive, an uncompiled component just behaves like normal React). **Don't switch to `compilationMode: 'all'`** — see the comment directly above the `plugins` array in `vite.config.ts` for the upstream bug it hits, why `'infer'` is safe, and what would justify revisiting this. Only hand-roll memoization if you've profiled a specific, measured problem the compiler doesn't catch, and document why inline if you do.
- **Sidebar version marker** (`v{__APP_VERSION__} · {__COMMIT_HASH__} · self-hosted`) is generated, not hardcoded: `__APP_VERSION__`/`__COMMIT_HASH__` are injected by `vite.config.ts`'s `define` (reading `package.json`'s `version` and `git rev-parse`/the `GIT_SHA` env var — see the Dockerfile). Bump the version in `package.json`, not in `Sidebar.tsx`.
- **Constants discipline — no hardcoded strings in components, full stop.** Every literal that renders as UI text or a meaningful prop value (headings, paragraph copy, button/link labels, empty/loading/error-state messages, `placeholder`, `aria-label`, `title` tooltips) belongs in a named constant, not typed inline in JSX — even if it currently appears exactly once. Two files split this by concern:
  - **`src/strings.ts`** — user-facing UI text, one export per screen/component (`HOME_PAGE_STRINGS`, `DISCORD_PAGE_STRINGS`, `CONNECTED_SERVERS_STRINGS`, `SERVER_CARD_STRINGS`, `SLASH_COMMANDS_STRINGS`, `BACKEND_HEALTH_STRINGS`, `SETTINGS_PAGE_STRINGS`, `NOT_FOUND_STRINGS`, `SIDEBAR_STRINGS`, `TOPBAR_STRINGS`, `TOAST_CONTAINER_STRINGS`, `NAV_STRINGS`, `PAGE_TITLES`). A new screen gets its own `<SCREEN>_STRINGS` export here — group by screen, not one flat namespace.
  - **`src/constants.ts`** — non-text config: API endpoints, timing, external IDs/URLs, and message *templates* that take an argument (`CONFIRM_MESSAGES`, `TOAST_MESSAGES` — these stay here, not in `strings.ts`, since they're parameterized functions built from runtime data, not static screen text).
  - **`src/routes.ts`** — `ROUTES`/`ROUTE_SEGMENTS` for paths (see the routing bullet above).

  **When the exact same word already appears as a different constant, reuse it — don't retype it.** `PAGE_TITLES` (`OVERVIEW`/`DISCORD_BOT`/`SETTINGS`) is shared verbatim across `router.tsx`'s `staticData.title`, `navConfig.ts`'s section/item labels, `SettingsPage.tsx`'s heading, and `TopBar.tsx`'s settings icon label — four call sites, one constant. Don't force a merge where the current text genuinely differs by design, though (e.g. the Home nav item's label is `"Home"` while the page it links to is titled `"Overview"` — that's an intentional UX pattern, not duplication, so those stay as separate constants).

  Real gaps this closed: `CURRENT_USER.NAME`/`.ROLE`, `NOT_FOUND_TITLE`, `DEFAULT_ERROR_REASON` (all previously duplicated verbatim across 2+ files with no shared source — one of them, `NOT_FOUND_TITLE`, had a comment admitting the duplication had to be kept in sync by hand); every `createRoute({ path })` segment in `router.tsx`; and every remaining literal across `HomePage.tsx`, `DiscordPage.tsx`, `ConnectedServersTab.tsx`, `ServerCard.tsx`, `SlashCommandsTab.tsx`, `BackendHealthTab.tsx`, `SettingsPage.tsx`, `NotFoundPage.tsx`, `Sidebar.tsx`, `TopBar.tsx`, `ToastContainer.tsx`, and `navConfig.ts` — a full sweep, not a sample.

## Allowed changes

- `src/components/ui/` — Shared design-system primitives (`Avatar`, `Button`, `Card`, `Pill`, `KpiTile`, `SearchInput`, `TabButton`, `StatusDot`, `ChannelIcon`, ...). New primitives follow the same `Name.tsx` + `Name.module.css` pair convention and get re-exported from `index.ts`
- `src/components/layout/` — App shell (`AppShell.tsx`, `Sidebar.tsx`, `TopBar.tsx`, `navConfig.ts`, `ToastContainer.tsx`)
- `src/components/` — Page components (`home/`, `discord/` + its `tabs/`, `settings/`, `NotFoundPage.tsx`)
- `src/hooks/` — Custom React hooks (`discordQueries.ts` — TanStack Query wrappers, `useToast.ts`)
- `src/services/` — API service clients calling backend endpoints
- `src/context/` — React Context providers (`ToastProvider.tsx`)
- `src/router.tsx` — The TanStack Router route tree (`createRootRoute`/`createRoute`/`createRouter`)
- `src/routes.ts` — Centralized route paths (`ROUTES`), consumed by both `router.tsx` and navigation call sites
- `src/constants.ts` — API endpoints, timing config, and parameterized confirm/toast message templates (see the constants-discipline bullet above)
- `src/strings.ts` — Static UI text, grouped per screen/component (see the constants-discipline bullet above)
- `src/types/` — TypeScript interfaces and models
- `src/App.tsx`, `src/main.tsx`, `src/index.css`, `src/index.html` — Application shell (providers) and root
- `vite.config.ts` — Vite configuration (the app-specific object merged on top of the shared base)
- `eslint.config.mjs` — Only if adding app-specific overrides on top of `@angora/config/eslint/react.mjs`; put reusable rules in the shared package instead
- `package.json` — Dependencies (check the license of anything new — see the root AGENTS.md's [Licensing](../../AGENTS.md#licensing) section)
- `Dockerfile` — Container configuration

## Forbidden changes

- Don't change port 3000
- Don't break the API proxy to `/api`
- Don't remove `nginx.conf`

## Verification

After changes, run from the repo root (or `apps/frontend/` without `--filter angora-frontend`):

```bash
pnpm install
pnpm --filter angora-frontend run lint
pnpm --filter angora-frontend exec tsc --noEmit
pnpm --filter angora-frontend exec tsc --noEmit -p tsconfig.node.json
pnpm --filter angora-frontend run test
pnpm run format:check
```

## Troubleshooting

**Frontend can't reach backend**: verify the backend is running (`docker ps`), check the nginx proxy config in the frontend Dockerfile, test the backend directly with `curl http://localhost:8080/api/health`.
