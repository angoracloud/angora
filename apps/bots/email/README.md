# Email bot

Node.js 24 + TypeScript 7 service (`angora-email-bot`) that talks to the backend. Configs extend [`@angora/config`](../../../packages/config/README.md).

See the [root README](../../../README.md) for the compose quickstart and repo-wide concerns. The [Discord](../discord/README.md) and [Slack](../slack/README.md) bots share this layout and these commands.

## Running

```bash
docker-compose up --build email-bot          # from the repo root

cd apps/bots/email && pnpm run build && pnpm run start
```

## Commands

Run from `apps/bots/email/`, or as `pnpm --filter angora-email-bot run <script>`.

| Command | What it does |
| ------- | ------------ |
| `pnpm run lint` | ESLint |
| `pnpm run build` | `tsc` — `src/` to `dist/`, also the typecheck step |
| `pnpm run test` | Vitest — a placeholder, see [Limitations](../../../README.md#limitations) |
| `pnpm run start` | `node dist/index.js` |

## Health check

`src/index.ts` runs a minimal HTTP server on `3003` serving `GET /health`. That's what `docker-compose.yml` probes, and what keeps the container alive as a long-lived process. The port isn't published to the host — it's reachable only inside `angora-network`.

Inside Docker, reach the backend by service name: `http://backend:8080/...`, never `localhost`.

## Secrets

This bot has no secrets of its own yet, but still calls `loadSecrets()` from [`@angora/secrets`](../../../packages/secrets/README.md) at startup and discards the result, so a broken Infisical config fails it at boot like every other service. With `INFISICAL_ENABLED` off, that call just reads `process.env`.

When it grows real credentials, read them from that provider, not `process.env`.

## Notes

- `package.json` declares `"type": "module"` — don't remove it.
- Nothing visible happening? `docker-compose logs email-bot`.
