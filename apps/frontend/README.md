# Frontend

React web application, served by nginx in production.

- **Framework**: React 19, TypeScript 7
- **Bundler**: Vite 8
- **Config**: TypeScript/ESLint/Vite configs are extended from [`@angora/config`](../../packages/config/README.md), the shared config package

See the [root README](../../README.md) for the one-command `docker-compose up --build` quickstart and repo-wide concerns (environment variables, CI, dependency guardrails).

## Running

**Via Docker** (from the repo root): `docker-compose up --build frontend` — production build, served by nginx. To bake the current commit into the sidebar's version marker (shown as `v{version} · {commit} · self-hosted`), set `GIT_SHA` before building — `.git` isn't in the Docker build context, so it can't be read automatically: `GIT_SHA=$(git rev-parse --short HEAD) docker-compose up --build frontend`. Without it, the build falls back to `unknown`.

**Locally**, for hot-reloading dev server: with a backend reachable at `http://localhost:8080` (either `docker-compose up -d postgres backend` from the repo root, or running the backend locally per its own README), run:

```bash
cd apps/frontend
pnpm dev
```

Or from the repo root, without `cd`-ing in: `pnpm run dev:frontend` — same command, just a shortcut.

This serves the app at [http://localhost:3000](http://localhost:3000) and proxies `/api` requests to `http://localhost:8080`.

## Commands

| Command                                          | What it does                                              |
| -------------------------------------------------- | ------------------------------------------------------------ |
| `pnpm run lint`                                   | ESLint                                                        |
| `pnpm run typecheck`                              | `tsc --noEmit`                                                |
| `pnpm --filter angora-frontend exec tsc --noEmit -p tsconfig.node.json` | Second typecheck pass, needed because the default `typecheck` script doesn't cover `tsconfig.node.json` |
| `pnpm run test`                                   | Vitest — currently just a placeholder smoke test, see the root README's [Limitations](../../README.md#limitations) |
| `pnpm run build`                                  | Production build via Vite (`dist/`)                          |

Run these from `apps/frontend/`, or from the repo root as `pnpm --filter angora-frontend run <script>`.

## Architecture & Directory Structure

The frontend is structured in an N-tier modular architecture for clean separation of concerns:

```
src/
├── components/
│   ├── ui/               # Shared design-system primitives (Avatar, Button, Card, Pill,
│   │                      #   KpiTile, SearchInput, TabButton, StatusDot, ChannelIcon, ...)
│   │                      #   — every screen composes these, no bespoke per-screen CSS
│   ├── layout/            # App shell: AppShell.tsx (Sidebar + TopBar + <Outlet/>),
│   │                      #   Sidebar.tsx, TopBar.tsx, navConfig.ts, ToastContainer.tsx
│   ├── home/              # Overview / Dashboard home page
│   ├── discord/           # Discord Bot Manager page & sub-tabs (nested routes)
│   │   └── tabs/          # ConnectedServersTab, SlashCommandsTab, BackendHealthTab, ServerCard
│   ├── settings/          # SettingsPage (placeholder — sidebar switches to its admin/settings nav here)
│   └── NotFoundPage.tsx   # Catch-all 404, still rendered inside AppShell
├── context/              # React Context definitions & providers (ToastProvider)
├── hooks/                # Custom React hooks (discordQueries — TanStack Query wrappers, useToast)
├── services/             # API client layer (discordService) calling backend endpoints
├── router.tsx            # TanStack Router route tree (createRootRoute/createRoute/createRouter),
│                          #   AppShell as the root route's component
├── routes.ts             # ROUTES (full paths, for Link/navigate/redirect) and ROUTE_SEGMENTS
│                          #   (bare segments, for router.tsx's createRoute) — one source of truth
├── constants.ts          # API endpoints, timing config, confirm/toast message templates,
│                          #   and other config-like or duplicated values
├── types/                # TypeScript interfaces (DiscordServer, ToastNotification, etc.)
├── App.tsx               # QueryClientProvider + ToastProvider + <RouterProvider router={router}/>
├── main.tsx              # React DOM root entrypoint
├── index.css             # Global design tokens (color/space/radius/type/shadow) + reset + base
└── index.html            # SPA root HTML template
```

### Design system

- **Tokens (`index.css`)**: a single `:root` block of CSS custom properties — `--color-*`, `--space-*`, `--radius-*`, `--font-*`, `--shadow-*` — is the only global stylesheet. Everything else is scoped per component.
- **CSS Modules, one pair per component**: every component in `components/ui/` and `components/layout/` ships as `Name.tsx` + `Name.module.css` (e.g. `Avatar.tsx`/`Avatar.module.css`). Import styles as `import styles from './Name.module.css'` and reference classes via `styles.foo` — never a hardcoded class-name string, since CSS Modules hash/scope each class per file. The one exception is a component that intentionally reuses another component's visual style without rendering that component directly (e.g. `DiscordPage`'s `NavLink` tabs are styled like `TabButton` without being one) — those import the other component's `.module.css` directly (`import tabButtonStyles from '../ui/TabButton.module.css'`) rather than duplicating the CSS.
- **No bespoke CSS per screen**: page components (`home/`, `discord/`) compose `components/ui/` primitives and use inline `style={{...}}` only for one-off layout glue (flex/grid wrappers) referencing the same CSS custom properties — they don't define their own CSS files.
- **Fonts**: Hanken Grotesk (UI text) + JetBrains Mono (ids, counts, code), loaded via Google Fonts `<link>` tags in `index.html`.
- **React Compiler**: build-time auto-memoization (`babel-plugin-react-compiler`, wired via `@vitejs/plugin-react`'s `reactCompilerPreset()` + `@rolldown/plugin-babel` in `vite.config.ts`) — components don't hand-write `useMemo`/`useCallback`/`React.memo` for routine cases. Runs in the default, conservative `compilationMode: 'infer'`, so not every component gets compiled (that's expected — the compiler is purely additive, an uncompiled component just behaves like normal React); see `apps/frontend/AGENTS.md` for a known upstream limitation with the more aggressive `'all'` mode.

### Features & Systems

- **Routing (`@tanstack/react-router`, `router.tsx`, `routes.ts`)**: a code-based route tree (not file-based/codegen), with `AppShell` as the root route's component (`Sidebar` + `TopBar` + `<Outlet/>`). Each leaf route sets `staticData: { title }` (or `{ crumbs }`) that `TopBar` reads via `useMatches()` — no prop-drilled page titles. `DiscordPage`'s tabs are nested routes; each tab reads its own data directly via the TanStack Query hooks below (no outlet-context prop-threading needed — same query key means shared cache, not a duplicate request).
- **Data fetching (`@tanstack/react-query`, `hooks/discordQueries.ts`)**: `useDiscordServersQuery`/`useDiscordInviteQuery`/`useLeaveServerMutation` wrap the Discord API calls with automatic polling (`refetchInterval`), window-focus refetching, loading/error state, and cache dedup across every component that calls them (including the sidebar's live server-count badge) — replacing what used to be a hand-rolled `setInterval` + focus-listener + `isMounted`-guard hook.
- **Centralized Constants (`constants.ts`)**: backend endpoints (`API_ENDPOINTS`), timing intervals (`TIMING_CONFIG`), and confirm/toast message templates (`CONFIRM_MESSAGES`/`TOAST_MESSAGES`) are declared once as type-safe constants — see `apps/frontend/AGENTS.md`'s constants-discipline note for what belongs here versus staying inline. Route paths live separately in `routes.ts` (`ROUTES`/`ROUTE_SEGMENTS`), since they're consumed by both the route tree and navigation call sites.
- **Contextual Toast System**: A React Context (`ToastProvider` + `useToast`) provides auto-dismissing feedback notifications for user actions (bot leave, connection failures) without intercepting generic window errors.

## Notes

- **Vite root is `src/`**: `vite.config.ts` sets `root: 'src'` and `build.outDir: '../dist'` because `index.html` lives in `src/`, not the project root. Its script tag references `/main.tsx` (relative to that root), not `/src/main.tsx`.
- **Two `tsconfig` files, two purposes**: `tsconfig.json` extends `@angora/config/typescript/react-app.json` and covers `src/`; `tsconfig.node.json` extends `@angora/config/typescript/base.json` (not `react-app.json`) and covers `vite.config.ts` itself, which runs under Node, not the browser. That's why there are two separate typecheck commands above instead of one.
- **Path aliases**: TypeScript 7 dropped `baseUrl`, so `"paths": {"@/*": ["./src/*"]}` is set with no `baseUrl`. **This alias is not wired into Vite's `resolve.alias`** — it type-checks but 404s at runtime/build, so use relative imports (`../ui/Avatar`) until someone adds the matching Vite config.
- **API proxy**: `/api` requests are proxied to the backend — via `vite.config.ts` (`http://localhost:8080`) in the local dev server, via `nginx.conf` (`http://backend:8080`) in the production container.

## Troubleshooting

**Frontend shows errors / can't reach the backend**:

- Ensure the backend is running: `docker ps`
- Check the API proxy — the frontend calls `/api`, which proxies to the backend (see above)
- Verify backend logs: `docker-compose logs backend`
- Test the backend directly: `curl http://localhost:8080/api/health`
