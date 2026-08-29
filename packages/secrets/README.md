# @angora/secrets

Secret loading for the three bots ([slack](../../apps/bots/slack/README.md), [discord](../../apps/bots/discord/README.md), [email](../../apps/bots/email/README.md)). Not published; consumed only via `"@angora/secrets": "workspace:*"`.

`apps/frontend` doesn't use it — it's static assets served by nginx, with no runtime secrets. `apps/backend` has its own Kotlin version in [`src/config/`](../../apps/backend/src/config/).

This isn't part of [`@angora/config`](../config/README.md) because that package is tooling configuration; nothing in it ships in a running container.

## Usage

```ts
import { loadSecrets } from '@angora/secrets'

const secrets = await loadSecrets()
const token = secrets.get('SERVICE_TOKEN_DISCORD_BOT')
const url = secrets.get('BACKEND_URL', 'http://backend:8080')
```

With `INFISICAL_ENABLED` unset — the default — `loadSecrets()` is `process.env` and nothing touches the network. With it set to `true`, it authenticates to Infisical, fetches the secrets at the configured project/environment/path, and prefers those over the environment.

The variables it reads are documented in the root [`.env.example`](../../.env.example).

## Two things not to change without thinking

**It rejects rather than falling back.** If Infisical is enabled but unreachable, `loadSecrets()` rejects and callers exit. Serving environment values instead would let a production service boot on the `angora`/`angora` credentials in `docker-compose.yml` and report healthy. Don't catch and continue.

**Secrets are read once, at startup.** Rotating one needs a restart. There's no polling.

## No runtime dependencies

This calls Infisical's REST API with Node's global `fetch` rather than using `@infisical/sdk`. The SDK pulls `@aws-sdk/credential-providers`, smithy, zod, and `typescript@^5.5.4` as a runtime dependency, all behind `^` ranges — in a repo that pins exact versions and runs TypeScript 7. Two REST calls were cheaper. Keep `dependencies` empty.

The same two calls exist in two other places, and all three have to stay in sync:

| Where | Why it's separate |
| ------- | ------------------- |
| [`apps/backend/src/config/`](../../apps/backend/src/config/) | Different language |
| [`scripts/infisical-env.ts`](../../scripts/infisical-env.ts) | Runs on the host before containers start. It can't import this package: Node executes `.ts` directly but won't resolve a `.js` specifier to a `.ts` file, which is what the nodenext imports here use |

## Build order

The bots resolve this package's types from `dist/`, so it has to compile before any bot is typechecked or built. That's what the root `build:packages` script is for, and why root `typecheck` runs it first. `pnpm -r run build` orders it correctly on its own; the bots' Dockerfiles use `pnpm --filter angora-<name>-bot... run build` (note the `...`).

## Commands

```bash
pnpm --filter @angora/secrets run build
pnpm --filter @angora/secrets run test
pnpm --filter @angora/secrets run lint
```
