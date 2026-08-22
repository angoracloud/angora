# @angora/config

Shared TypeScript, ESLint, Prettier, and Vite configuration for the JS/TS side of the monorepo — [`apps/frontend`](../../apps/frontend/README.md) and all three bots ([slack](../../apps/bots/slack/README.md), [discord](../../apps/bots/discord/README.md), [email](../../apps/bots/email/README.md)). Not published to a real registry — consumed only via `"@angora/config": "workspace:*"`. `apps/backend` doesn't use this at all (no JS/TS tooling there).

## What's here

| Tool       | Files                                                              | Consumed as                                                                 |
| ------------ | --------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| TypeScript | `typescript/base.json`, `typescript/react-app.json`, `typescript/node.json` | `"extends": "@angora/config/typescript/react-app.json"` (or `node.json`) in each app's `tsconfig.json` |
| ESLint     | `eslint/base.mjs`, `eslint/react.mjs`, `eslint/node.mjs`             | `import reactConfig from '@angora/config/eslint/react.mjs'` in each app's `eslint.config.mjs` |
| Prettier   | `prettier/index.ts`                                                  | One repo-root `prettier.config.ts` re-exports it — Prettier is a single, repo-wide formatter, not a per-package one |
| Vite       | `vite/base.ts`                                                       | `mergeConfig(base, { ...appSpecificConfig })` in `apps/frontend/vite.config.ts` |

Only `apps/frontend` uses Vite today, but the base still lives here so any future Vite-based service starts from the same defaults instead of copy-pasting `apps/frontend/vite.config.ts`.

What's distinctive in each file:

- `typescript/base.json` — strictness options common to every package; `react-app.json` and `node.json` both extend it
- `typescript/react-app.json` — + browser/bundler/JSX settings (used by `apps/frontend`)
- `typescript/node.json` — + `nodenext` module settings and `"types": ["node"]` (used by all three bots; `apps/frontend`'s `tsconfig.node.json` extends `base.json` directly instead, since it only needs Node-flavored module resolution for `vite.config.ts`, not the Node type declarations). The explicit `types` array is load-bearing, not decoration: without it, `node:*` imports (e.g. `node:http`) fail to typecheck in some packages of this workspace even with `@types/node` installed — TypeScript's automatic `@types` discovery doesn't reliably walk pnpm's symlinked `node_modules/@types` layout here. Every package that extends `node.json` needs its own `"@types/node": "catalog:"` devDependency for this to resolve (see `AGENTS.md` in this directory for the rule).
- `eslint/base.mjs` — `@eslint/js` + `typescript-eslint` recommended rules
- `eslint/react.mjs` — `base` + `eslint-plugin-react-hooks`/`eslint-plugin-react-refresh` + `eslint-config-prettier` (used by `apps/frontend`)
- `eslint/node.mjs` — `base` + Node globals + `eslint-config-prettier` (used by all three bots)

## Why `eslint/*.mjs` isn't TypeScript like the rest

Every other config here is `.ts`, loaded natively (Node runs `.ts` directly; Vite and Prettier load their own `.ts` configs the same way) — no build step, no `ts-node`. ESLint's flat config loader *can* load a `.ts` file too, but only via an extra `jiti` dependency, and pulling that thread surfaced a real, unresolved incompatibility: `typescript-eslint` (every 8.x version, checked through 8.64.0, the newest available) crashes immediately on import against this repo's pinned TypeScript 7 — its code does `ts.Extension.Cjs` at module-load time, and TypeScript 7's Go-rewritten npm package doesn't export `Extension` the same way anymore. This isn't a lint-time-only failure or a peer-range nitpick; it's an unconditional crash the moment `typescript-eslint` is imported.

So `eslint/*.mjs` stays plain JS (with JSDoc types), and there's no `jiti` dependency anywhere in the repo. Don't convert it — see `AGENTS.md` in this directory for the full "forbidden changes" list.

## Why `package.json` pins its own `typescript@5.9.3`

This package's own `typescript` devDependency is pinned directly to `5.9.3`, **not** `"catalog:"` (which resolves to the repo's TypeScript 7). This is the actual fix for the crash above: pnpm resolves peer dependencies per-consumer, so because only `packages/config` declares this older TypeScript, `typescript-eslint`'s peer resolves to 5.9.3 *only inside this package's own dependency graph* — `apps/frontend`'s and the bots' own `tsc`/`vite build` still use the real TypeScript 7 from the catalog, unaffected. Don't change this pin to `catalog:`; that reintroduces the crash. If a future `typescript-eslint` release adds real TypeScript 7 support, this pin can be removed.

## Every app still needs its own `eslint`/`typescript` devDependency

pnpm's strict `node_modules` means a package only gets binaries for what it directly depends on. Depending on `@angora/config` alone gives an app the config *content*, not the `eslint`/`tsc` CLIs — each app's own `package.json` still declares `"eslint": "catalog:"` and `"typescript": "catalog:"` directly.

## Docker builds see this package too

`tsc`/`vite build` run *inside* the image for `apps/frontend` and the three bots, so for `extends`/`import` to resolve, `packages/config` has to be part of the build context. Their four Dockerfiles build from the **repo root** (`context: .` in `docker-compose.yml`), copying `pnpm-workspace.yaml`, the root `package.json`/lockfile, this package, and the app's own directory before `pnpm install --frozen-lockfile`.
