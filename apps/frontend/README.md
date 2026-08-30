# Frontend

React 19 + TypeScript 7 + Vite 8, served by nginx in production. TypeScript/ESLint/Vite configs extend [`@angora/config`](../../packages/config/README.md).

See the [root README](../../README.md) for the compose quickstart and repo-wide concerns.

## Running

**In Docker**, from the repo root: `docker-compose up --build frontend`. To bake the commit into the sidebar version marker, pass `GIT_SHA` — `.git` isn't in the build context, so it can't be read automatically:

```bash
GIT_SHA=$(git rev-parse --short HEAD) docker-compose up --build frontend
```

**Locally**, with a backend reachable on `:8080`:

```bash
cd apps/frontend && pnpm dev      # or `pnpm run dev:frontend` from the root
```

Serves [http://localhost:3000](http://localhost:3000) and proxies `/api` to `http://localhost:8080`.

## Commands

Run from `apps/frontend/`, or as `pnpm --filter angora-frontend run <script>`.

| Command | What it does |
| ------- | ------------ |
| `pnpm run lint` | ESLint |
| `pnpm run typecheck` | `tsc --noEmit` |
| `pnpm --filter angora-frontend exec tsc --noEmit -p tsconfig.node.json` | Second pass; the default script doesn't cover `tsconfig.node.json` |
| `pnpm run test` | Vitest — a placeholder smoke test, see [Limitations](../../README.md#limitations) |
| `pnpm run build` | Production build to `dist/` |

## Structure

```
src/
├── components/
│   ├── ui/          # Design-system primitives (Avatar, Button, Card, Pill, KpiTile, ...)
│   ├── layout/      # AppShell (Sidebar + TopBar + <Outlet/>), navConfig, ToastContainer
│   ├── home/        # Overview page
│   ├── discord/     # Discord Bot Manager + tabs/ (nested routes)
│   └── settings/    # Placeholder
├── context/         # ToastProvider
├── hooks/           # discordQueries (TanStack Query wrappers), useToast
├── services/        # API client layer
├── router.tsx       # TanStack Router tree; AppShell is the root component
├── routes.ts        # ROUTES (full paths) + ROUTE_SEGMENTS (bare, for createRoute)
├── constants.ts     # Endpoints, timing, parameterized confirm/toast templates
├── strings.ts       # Static UI text, grouped per screen
├── types/
└── index.css        # Design tokens + reset — the only global stylesheet
```

### Design system

- **Tokens**: one `:root` block in `index.css` (`--color-*`, `--space-*`, `--radius-*`, `--font-*`, `--shadow-*`). Everything else is component-scoped.
- **CSS Modules, one pair per component**: `Name.tsx` + `Name.module.css`, referenced as `styles.foo` — never a hardcoded class string, since classes are hashed per file. A component that deliberately borrows another's look imports that module directly (`import tabButtonStyles from '../ui/TabButton.module.css'`) rather than duplicating CSS.
- **No per-screen CSS**: pages compose `components/ui/` primitives, with inline `style={{...}}` only for one-off layout glue referencing the same tokens.
- **Fonts**: Hanken Grotesk for UI, JetBrains Mono for ids and counts, via Google Fonts in `index.html`.
- **React Compiler**: auto-memoization at build time, so components don't hand-write `useMemo`/`useCallback`/`React.memo`. Runs in the default `compilationMode: 'infer'`, so not every component is compiled — that's expected, the compiler is additive. See `AGENTS.md` for the `'all'` mode limitation.

### Features

- **Routing**: a code-based route tree, not file-based codegen. Each leaf sets `staticData: { title }` that `TopBar` reads via `useMatches()`, so titles aren't prop-drilled. Discord tabs are nested routes that fetch their own data — the same query key means shared cache, not duplicate requests.
- **Data fetching**: `hooks/discordQueries.ts` wraps the Discord calls with polling, focus refetching, loading/error state and cache dedup across every consumer including the sidebar badge. It replaced a hand-rolled `setInterval` + focus-listener + `isMounted` hook.
- **Constants and strings**: endpoints, timing and message templates in `constants.ts`; all static UI text in `strings.ts`, one export per screen; route paths in `routes.ts`. No hardcoded strings in JSX, even one-offs — see `AGENTS.md`.
- **Toasts**: `ToastProvider` + `useToast` for auto-dismissing action feedback, without intercepting window errors.

## Notes

- **Vite root is `src/`**, since `index.html` lives there; `build.outDir` is `../dist` and the script tag references `/main.tsx`.
- **Two tsconfigs**: `tsconfig.json` (extends `react-app.json`) covers `src/`; `tsconfig.node.json` (extends `base.json`) covers `vite.config.ts`, which runs under Node. Hence the two typecheck commands.
- **The `@/*` path alias is not wired into Vite's `resolve.alias`** — it typechecks but 404s at runtime. Use relative imports until someone adds it.
- **API proxy**: `/api` goes to `http://localhost:8080` via `vite.config.ts` in dev, and `http://backend:8080` via `nginx.conf` in the container.

## Troubleshooting

**Can't reach the backend**: check it's running (`docker ps`), check its logs (`docker-compose logs backend`), and test it directly (`curl http://localhost:8080/api/health`). Remember the frontend calls `/api`, which is proxied.
