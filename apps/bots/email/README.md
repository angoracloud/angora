# Email bot

Email processing bot (`angora-email-bot`) — Node.js service that talks to the backend.

- **Runtime**: Node.js 24+, TypeScript 7
- **Config**: TypeScript/ESLint configs are extended from [`@angora/config`](../../../packages/config/README.md), the shared config package

See the [root README](../../../README.md) for the one-command `docker-compose up --build` quickstart and repo-wide concerns (environment variables, CI, dependency guardrails). See the [Slack bot](../slack/README.md) and [Discord bot](../discord/README.md) READMEs — same layout, same commands.

## Running

**Via Docker** (from the repo root): `docker-compose up --build email-bot`

**Locally**:

```bash
cd apps/bots/email
pnpm run build
pnpm run start
```

## Commands

| Command             | What it does                                                                                     |
| --------------------- | ---------------------------------------------------------------------------------------------------- |
| `pnpm run lint`      | ESLint                                                                                                |
| `pnpm run build`     | `tsc` — compiles `src/` to `dist/` (also the typecheck step; excludes `*.test.ts` from the output)  |
| `pnpm run test`      | Vitest — currently just a placeholder smoke test, see the root README's [Limitations](../../../README.md#limitations) |
| `pnpm run start`     | `node dist/index.js`                                                                                 |

Run these from `apps/bots/email/`, or from the repo root as `pnpm --filter angora-email-bot run <script>`.

## Communicating with the backend

Inside Docker, use the service name as hostname: `http://backend:8080/...` (not `localhost`).

## Health check

`src/index.ts` starts a minimal HTTP server on port `3003` exposing `GET /health` (`200 {"status":"ok"}`) — this is what `docker-compose.yml`'s healthcheck for `email-bot` probes, and it's also what keeps the container running as a long-lived process. The port isn't published to the host, only reachable inside `angora-network`.

## Notes

- `package.json` declares `"type": "module"` — don't remove it.
- Not running / not doing anything visible: check `docker-compose logs email-bot`.

## Secrets

This bot has no secrets of its own yet. It still calls `loadSecrets()` from [`@angora/secrets`](../../../packages/secrets/README.md) at startup and discards the result. That way a broken Infisical config fails it at boot, like every other service, instead of leaving it healthy while the rest of the stack is down.

With `INFISICAL_ENABLED` off, the default, the call just reads `process.env`.

When this bot grows real credentials, read them from that provider rather than `process.env` directly.
