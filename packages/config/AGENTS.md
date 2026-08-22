# AI Agent Guidelines — Shared config (`@angora/config`)

Scoped to `packages/config/`. See the [root AGENTS.md](../../AGENTS.md) for repo-wide rules. Read [`README.md`](README.md) in this directory first — it explains *why* the rules below exist, not just what they are.

- Single source of truth for TypeScript, ESLint, Prettier, and Vite configuration, consumed by `apps/frontend` and all three bots via `"@angora/config": "workspace:*"`.
- **If a rule/compiler-option/format-setting should apply to more than one package, it belongs here — not copy-pasted into each app.**
- `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`'s exact export shapes are version-sensitive and have changed between releases (e.g., `reactRefresh.configs.vite` is a plain object in the pinned version, not a factory function — don't assume the shape from a README or an older version; check `node --input-type=module -e "import p from '<pkg>'; console.log(p.configs)"` from inside `packages/config` after any version bump).
- Prettier is intentionally **not** per-package: `prettier.config.ts` at the repo root re-exports `@angora/config/prettier/index.ts`, and that's the only Prettier config in the repo.
- **`typescript/`, `prettier/`, `vite/` are `.ts`/`.json`; `eslint/` is deliberately plain `.mjs` — do not convert it.** See `README.md` for the full explanation (`typescript-eslint` hard-crashes on import against TypeScript 7). If you're tempted to convert `eslint/*.mjs` to `.ts` for consistency, don't — confirmed broken as of typescript-eslint 8.64.0 (the newest version available when this was diagnosed).
- **`package.json` pins `"typescript": "5.9.3"` directly instead of `"typescript": "catalog:"`** — this is the workaround for the crash above (see `README.md`). Don't change this pin to `catalog:`.
- **`typescript/node.json` sets `"types": ["node"]`** — any package that extends it (currently all three bots) must have its own `"@types/node": "catalog:"` devDependency, or `node:*` imports won't typecheck (see `README.md` for why). Don't remove this `types` array without re-verifying `node:*` imports still typecheck across all three bots first.

## Allowed changes

- Anything under `typescript/`, `eslint/` (as plain `.mjs`, see above), `prettier/`, `vite/`
- `package.json` — Dependencies (keep entries pointed at `catalog:` where the version is also used elsewhere, **except** the intentional `typescript: 5.9.3` pin above; check the license of anything new — see the root AGENTS.md's [Licensing](../../AGENTS.md#licensing) section)

## Forbidden changes

- Don't add `exports` restrictions to `package.json` that would break the deep imports (`@angora/config/eslint/react.mjs`, etc.) apps already use
- Don't convert `eslint/base.mjs`, `eslint/react.mjs`, or `eslint/node.mjs` to `.ts`, and don't add `jiti` as a dependency anywhere in the repo

## Docker

The four JS/TS Dockerfiles (`apps/frontend` + 3 bots) build from the repo root, not their own directory, specifically so `tsc`/`vite build` running inside the image can resolve `@angora/config`. If you add a new Vite- or tsc-based service, its `docker-compose.yml` entry needs `context: .` + an explicit `dockerfile:` path (not `context: ./apps/<service>`), and its Dockerfile needs to `COPY` `pnpm-workspace.yaml`, the root `package.json`/`pnpm-lock.yaml`, `packages/config`, and its own `apps/<service>` directory before `pnpm install --frozen-lockfile`. Copy `apps/frontend` and `apps/bots` too even in a bot's Dockerfile — `pnpm install --frozen-lockfile` expects the on-disk package set to match every importer in the lockfile, not just the one you're building.
