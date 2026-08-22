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
- **Routing** uses `react-router` (not `react-router-dom` — v8's DOM bindings live in the main package). Route paths are centralized in `src/routes.ts` (`ROUTES`), not scattered as string literals. `App.tsx` uses the **data router** (`createBrowserRouter`/`RouterProvider`), not plain `<BrowserRouter>`/`<Routes>` — `TopBar`'s `useMatches()` requires it. Don't switch back to declarative-only mode without also replacing that mechanism.
- **Sidebar version marker** (`v{__APP_VERSION__} · {__COMMIT_HASH__} · self-hosted`) is generated, not hardcoded: `__APP_VERSION__`/`__COMMIT_HASH__` are injected by `vite.config.ts`'s `define` (reading `package.json`'s `version` and `git rev-parse`/the `GIT_SHA` env var — see the Dockerfile). Bump the version in `package.json`, not in `Sidebar.tsx`.

## Allowed changes

- `src/components/ui/` — Shared design-system primitives (`Avatar`, `Button`, `Card`, `Pill`, `KpiTile`, `SearchInput`, `TabButton`, `StatusDot`, `ChannelIcon`, ...). New primitives follow the same `Name.tsx` + `Name.module.css` pair convention and get re-exported from `index.ts`
- `src/components/layout/` — App shell (`AppShell.tsx`, `Sidebar.tsx`, `TopBar.tsx`, `navConfig.ts`, `ToastContainer.tsx`)
- `src/components/` — Page components (`home/`, `discord/` + its `tabs/`, `settings/`, `NotFoundPage.tsx`)
- `src/hooks/` — Custom React hooks (`useDiscordServers.ts`, `useToast.ts`)
- `src/services/` — API service clients calling backend endpoints
- `src/context/` — React Context providers (`ToastProvider.tsx`)
- `src/routes.ts` — Centralized route paths (`ROUTES`), consumed by both the route tree and navigation call sites
- `src/constants.ts` — API endpoints, timing config, and toast message templates
- `src/types/` — TypeScript interfaces and models
- `src/App.tsx`, `src/main.tsx`, `src/index.css`, `src/index.html` — Application shell, route tree, and root
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
