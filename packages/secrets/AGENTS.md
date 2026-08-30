# AI Agent Guidelines — `@angora/secrets`

Scoped to `packages/secrets/`. See the [root AGENTS.md](../../AGENTS.md) for repo-wide rules. Read [`README.md`](README.md) in this directory first — it explains *why* the rules below exist.

- Runtime secret loading for the three bots: `process.env` by default, Infisical when `INFISICAL_ENABLED=true`. The backend has its own Kotlin equivalent in `apps/backend/src/config/`.
- **`dependencies` must stay empty.** This package talks to Infisical's REST API over Node's global `fetch` specifically to avoid `@infisical/sdk`'s tree (`@aws-sdk/credential-providers`, smithy, zod, and a *runtime* `typescript@^5.5.4`, all behind `^` ranges). Don't add the SDK, and don't add an HTTP client.
- **`loadSecrets()` must keep rejecting when Infisical is enabled and unavailable.** Never make it fall back to the environment on error, and don't add a flag that does: services would then boot on the dev credentials in `docker-compose.yml` while looking healthy. `INFISICAL_ENABLED=false` is the only off switch.
- **Env var names and defaults live in `src/constants.ts`**, mirroring the backend's `BackendConstants.Infisical`. Those two, plus `scripts/infisical-env.ts`, describe the same API — change one, check the others. Error message strings stay inline; the backend's constants rule doesn't apply here.
- Secrets are resolved once at startup. Don't add polling or a refresh timer without a deliberate decision about what a mid-flight credential change does to in-progress work.

## Allowed changes

- `src/` — loader, Infisical client, constants, types, tests
- `package.json` — devDependencies only (check the license of anything new, see the root AGENTS.md's [Licensing](../../AGENTS.md#licensing) section)

## Forbidden changes

- Don't add anything to `dependencies`
- Don't add `exports` restrictions that break the bots' `import { loadSecrets } from '@angora/secrets'`
- Don't point `types` at `src/` — the bots compile with `rootDir: ./src`, and a source-level type entry pulls this package's files outside their rootDir

## Build order

The bots resolve this package's types from `dist/`, so it compiles first or their typecheck fails. The root `typecheck` script runs `build:packages` before `tsc`, and each bot's Dockerfile uses `pnpm --filter angora-<name>-bot... run build` (the `...` includes workspace dependencies). If you add a fourth consumer, it needs the same treatment.
