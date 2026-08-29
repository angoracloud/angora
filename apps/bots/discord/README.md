# Discord bot

Node.js 24 + TypeScript 7 service (`angora-discord-bot`) that talks to the backend. Configs extend [`@angora/config`](../../../packages/config/README.md).

See the [root README](../../../README.md) for the compose quickstart and repo-wide concerns. The [Slack](../slack/README.md) and [Email](../email/README.md) bots share this layout and these commands.

## Running

```bash
docker-compose up --build discord-bot          # from the repo root

cd apps/bots/discord && pnpm run build && pnpm run start
```

## Commands

Run from `apps/bots/discord/`, or as `pnpm --filter angora-discord-bot run <script>`.

| Command | What it does |
| ------- | ------------ |
| `pnpm run lint` | ESLint |
| `pnpm run build` | `tsc` — `src/` to `dist/`, also the typecheck step |
| `pnpm run test` | Vitest — a placeholder, see [Limitations](../../../README.md#limitations) |
| `pnpm run start` | `node dist/index.js` |

## Structure

```
src/
├── client/discordClient.ts       # Gateway listeners: ready, guildCreate, guildDelete, interactionCreate
├── server/internalHttpServer.ts  # :3001 — GET /health, POST /leave/:guildId
├── services/
│   ├── backendService.ts         # POST /api/discord/bot/sync
│   └── commandService.ts         # Slash commands (/ping)
├── constants.ts                  # Ports, URLs, intervals, endpoints
├── types/
└── index.ts                      # Bootstraps the client and HTTP server
```

## Lifecycle

1. **Startup** — the Discord client and the internal HTTP server both start unconditionally, even without a real token. Only the gateway login is conditional (see Notes).
2. **Invited to a guild** — `guildCreate` syncs it to the backend.
3. **Every 60s** — `syncAllGuilds` refreshes member counts and guild info.
4. **Disconnected from the Angora UI** — the backend calls `POST /leave/:guildId`, and the bot leaves.
5. **Kicked in Discord** — `guildDelete` tells the backend `botJoined: false`.

## Authenticating to the backend

`POST /api/discord/bot/sync` requires a service token; being on the Docker network isn't enough. The bot sends `Authorization: Bearer ${SERVICE_TOKEN_DISCORD_BOT}` and the backend registers that value's hash at startup, so **both must see the identical value**. `docker-compose.yml` wires one variable into both. If they disagree, syncing fails with `401` and the bot logs a hint naming the variable; nothing else about the bot is affected.

That token, plus `DISCORD_BOT_TOKEN`, `DISCORD_CLIENT_ID` and `BACKEND_URL`, is resolved at startup via [`@angora/secrets`](../../../packages/secrets/README.md) rather than read from `process.env` at import time, which is what lets Infisical supply them. `configureBackendService()` must run before any sync — `index.ts` calls it right after `loadSecrets()`. Until it does, `syncGuildWithBackend()` logs and returns instead of syncing unauthenticated.

## Notes

- `package.json` declares `"type": "module"` — don't remove it.
- Nothing visible happening? `docker-compose logs discord-bot`.
- **Without a real `DISCORD_BOT_TOKEN`** (the default) the bot stays passive: no gateway connection and no guilds cached. The `:3001` server still starts, so the healthcheck's `GET /health` returns `200`, and `POST /leave/:guildId` is reachable but a no-op with no guild in cache.
