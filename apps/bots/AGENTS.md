# AI Agent Guidelines — Bots

Scoped to `apps/bots/*/` (slack, discord, email). All three are identical in structure — see the [root AGENTS.md](../../AGENTS.md) for repo-wide rules, and [`packages/config/AGENTS.md`](../../packages/config/AGENTS.md) for the shared TypeScript/ESLint config these consume.

- **Runtime**: Node.js 24+ + TypeScript 7
- **Build**: pnpm + `tsc`
- **Module resolution**: `moduleResolution: "nodenext"` (TypeScript 7 removed the old `"node"`/node10 resolution mode); keep `module` set to `"nodenext"` too since they must match.
- **`tsconfig.json` extends `@angora/config/typescript/node.json`; `eslint.config.mjs` re-exports `@angora/config/eslint/node.mjs`** — see `packages/config/AGENTS.md`.
- **`Dockerfile` builds from the repo root**, not the bot's own directory.
- **`tsconfig.json` excludes `src/**/*.test.ts`** from the compiled `dist/` output — the `build`/`start` scripts should never ship the placeholder Vitest test.
- **Every bot starts a minimal `node:http` server unconditionally and exposes `GET /health`** returning `200 {"status":"ok"}` on a fixed port (slack `3002`, discord `3001`, email `3003`) — this is what `docker-compose.yml`'s healthcheck for each bot probes (`wget --spider`, not `curl` — not present on `node:24-alpine`), and it's also what keeps the container running as a long-lived process instead of exiting right after its startup log line. The handler must accept both `GET` and `HEAD` — `wget --spider` sends `HEAD`, not `GET`. None of these ports are published to the host (`ports:` in `docker-compose.yml`); they're only reachable inside `angora-network`.

## Allowed changes

- `src/` — Bot logic (`index.ts`, `client/`, `server/`, `services/`, `constants.ts`, `types/`)
- `package.json` — Dependencies (check the license of anything new — see the root AGENTS.md's [Licensing](../../AGENTS.md#licensing) section)
- `Dockerfile` — Container configuration

## Forbidden changes

- Don't change the start command (`node dist/index.js`)
- Don't remove `"type": "module"`

## Common tasks

### Add a new bot

1. Create directory `apps/bots/new-bot/`
2. Add `src/index.ts` with bot logic, including the unconditional `node:http` server on a new fixed port (pick the next unused one after `3001`/`3002`/`3003`) exposing `GET /health` (accepting `HEAD` too — see above); copy the pattern from `apps/bots/slack/src/index.ts` or `apps/bots/email/src/index.ts` if the bot has no other HTTP surface, or from `apps/bots/discord/src/server/internalHttpServer.ts` if it needs other routes alongside `/health`
3. Add `package.json` — `"typescript": "catalog:"`, `"eslint": "catalog:"`, `"vitest": "catalog:"`, `"@types/node": "catalog:"`, `"@angora/config": "workspace:*"` as devDependencies, plus `build`/`start`/`lint`/`test` scripts (copy an existing bot's `package.json` as the template)
4. Add `tsconfig.json` that extends `@angora/config/typescript/node.json`, excludes `src/**/*.test.ts`, and `eslint.config.mjs` that re-exports `@angora/config/eslint/node.mjs` (copy an existing bot's files — they're all identical except `outDir`/`rootDir`, which don't even vary)
5. Add a placeholder `src/placeholder.test.ts` (copy an existing bot's) so CI's test step has something to run
6. Add `Dockerfile` for containerization — copy an existing bot's `Dockerfile`, update the two `apps/bots/<name>` path segments, and add `EXPOSE <port>` for the health port; it must build from the repo root context (see `packages/config/AGENTS.md`)
7. Add the service to `docker-compose.yml` with `context: .` + `dockerfile: apps/bots/new-bot/Dockerfile` (not `context: ./apps/bots/new-bot`), `depends_on: backend: condition: service_healthy`, and a `healthcheck:` block (`wget --spider -q http://localhost:<port>/health`, matching the other three bots)
8. `pnpm-workspace.yaml`'s `apps/bots/**` glob already covers it — no change needed there unless the new bot needs its own catalog entry
9. Add the new `package.json` path to the `manifests` list in `scripts/check-dependency-age.ts`

## Verification

After changes, run from the repo root:

```bash
pnpm install
pnpm --filter <pkg> run lint
pnpm --filter <pkg> run build
pnpm --filter <pkg> run test
pnpm run format:check
```
