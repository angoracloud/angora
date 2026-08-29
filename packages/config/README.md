# @angora/config

Shared TypeScript, ESLint, Prettier and Vite configuration for [`apps/frontend`](../../apps/frontend/README.md) and the three bots. Not published; consumed via `"@angora/config": "workspace:*"`. `apps/backend` has no JS tooling and doesn't use it.

## What's here

| Tool | Files | Consumed as |
| ---- | ----- | ----------- |
| TypeScript | `typescript/{base,react-app,node}.json` | `"extends": "@angora/config/typescript/react-app.json"` |
| ESLint | `eslint/{base,react,node}.mjs` | `import reactConfig from '@angora/config/eslint/react.mjs'` |
| Prettier | `prettier/index.ts` | The one repo-root `prettier.config.ts` re-exports it |
| Vite | `vite/base.ts` | `mergeConfig(base, { ... })` in `apps/frontend/vite.config.ts` |

Only the frontend uses Vite today; the base lives here so a future Vite service doesn't start by copy-pasting.

- `typescript/base.json` — strictness common to every package; the other two extend it
- `typescript/react-app.json` — browser, bundler and JSX settings
- `typescript/node.json` — `nodenext` modules and `"types": ["node"]`, used by the bots. That explicit `types` array matters: without it, `node:*` imports fail to typecheck even with `@types/node` installed, because TypeScript's automatic `@types` discovery doesn't reliably walk pnpm's symlinked layout. Every package extending it needs its own `"@types/node": "catalog:"`. (`apps/frontend/tsconfig.node.json` extends `base.json` instead — it needs the module resolution, not the Node types.)
- `eslint/base.mjs` — `@eslint/js` + `typescript-eslint` recommended
- `eslint/react.mjs` / `eslint/node.mjs` — base, plus React plugins or Node globals, plus `eslint-config-prettier`

## Why `eslint/*.mjs` isn't TypeScript

Every other config here is `.ts`, loaded natively with no build step. ESLint's flat config can load `.ts` too, but only via `jiti` — and pulling that thread surfaced a real incompatibility: `typescript-eslint` (every 8.x through 8.64.0) crashes on import against this repo's TypeScript 7, because it evaluates `ts.Extension.Cjs` at module load and TypeScript 7's Go-rewritten package no longer exports `Extension` that way. Not a peer-range nitpick — an unconditional crash.

So these stay plain JS with JSDoc types, and there's no `jiti` anywhere in the repo. See `AGENTS.md` here before changing that.

## Why `package.json` pins `typescript@5.9.3`

This package pins its own `typescript` to `5.9.3` rather than `catalog:`, and that pin is the fix for the crash above. pnpm resolves peers per-consumer, so `typescript-eslint` sees 5.9.3 only inside this package's graph — the frontend and bots still compile with TypeScript 7 from the catalog. Don't switch it to `catalog:`. It can be removed once `typescript-eslint` supports TypeScript 7.

## Two consequences

**Every app still declares its own `eslint`/`typescript`.** pnpm's strict `node_modules` gives a package binaries only for its direct dependencies, so depending on `@angora/config` provides the config content, not the CLIs.

**Docker builds need this package in context.** `tsc`/`vite build` run inside the image, so the four Dockerfiles build from the repo root (`context: .`) and copy `pnpm-workspace.yaml`, the root manifest and lockfile, this package, and the app directory before `pnpm install --frozen-lockfile`.
