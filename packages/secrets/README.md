# @angora/secrets

Secret loading for the three bots. Not published; consumed via `"@angora/secrets": "workspace:*"`.

The frontend doesn't use it (static assets, no runtime secrets), and the backend has its own Kotlin version in [`src/config/`](../../apps/backend/src/config/). It isn't part of [`@angora/config`](../config/README.md) because that package is tooling config — none of it ships in a running container.

## Usage

```ts
import { loadSecrets } from '@angora/secrets'

const secrets = await loadSecrets()
const token = secrets.get('SERVICE_TOKEN_DISCORD_BOT')
const url = secrets.get('BACKEND_URL', 'http://backend:8080')
```

With `INFISICAL_ENABLED` unset, `loadSecrets()` is `process.env` and nothing touches the network. Set to `true`, it authenticates to Infisical, fetches the configured project/environment/path, and prefers those values. Variables are documented in the root [`.env.example`](../../.env.example).

## Two things not to change without thinking

**It rejects rather than falling back.** If Infisical is enabled but unreachable, `loadSecrets()` rejects and callers exit. Don't catch and continue — the root [README](../../README.md#secret-management-infisical) explains what a silent fallback would boot on.

**Secrets are read once, at startup.** Rotating needs a restart; there's no polling.

## No runtime dependencies

This calls Infisical's REST API with Node's global `fetch`. `@infisical/sdk` pulls `@aws-sdk/credential-providers`, smithy, zod and a runtime `typescript@^5.5.4`, all behind `^` ranges, into a repo that pins exact versions on TypeScript 7. Keep `dependencies` empty.

The same two calls exist in [`apps/backend/src/config/`](../../apps/backend/src/config/) (different language) and [`scripts/infisical-env.ts`](../../scripts/infisical-env.ts) (runs on the host, and can't import this package because Node won't resolve a `.js` specifier to a `.ts` file). All three must stay in sync.

## Build order

The bots resolve types from `dist/`, so this compiles first or their typecheck fails. Root `typecheck` runs `build:packages` beforehand; `pnpm -r run build` orders it correctly on its own; the bots' Dockerfiles use `pnpm --filter angora-<name>-bot... run build` (note the `...`).

## Commands

```bash
pnpm --filter @angora/secrets run {build,test,lint}
```
