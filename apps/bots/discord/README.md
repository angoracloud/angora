# Discord bot

Discord integration bot (`angora-discord-bot`) — Node.js service that talks to the backend.

- **Runtime**: Node.js 24+, TypeScript 7
- **Config**: TypeScript/ESLint configs are extended from [`@angora/config`](../../../packages/config/README.md), the shared config package

See the [root README](../../../README.md) for the one-command `docker-compose up --build` quickstart and repo-wide concerns (environment variables, CI, dependency guardrails). See the [Slack bot](../slack/README.md) and [Email bot](../email/README.md) READMEs — same layout, same commands.

## Running

**Via Docker** (from the repo root): `docker-compose up --build discord-bot`

**Locally**:

```bash
cd apps/bots/discord
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

Run these from `apps/bots/discord/`, or from the repo root as `pnpm --filter angora-discord-bot run <script>`.

## Architecture & Structure

```
src/
├── client/              # Discord.js client initialization & gateway event handlers
│   └── discordClient.ts # Ready, guildCreate, guildDelete, interactionCreate listeners
├── server/              # Internal HTTP server for backend-to-bot communication
│   └── internalHttpServer.ts # Listens on :3001 for GET /health and POST /leave/:guildId
├── services/            # Bot business logic & backend HTTP client
│   ├── backendService.ts # Sends POST /api/discord/bot/sync to backend
│   └── commandService.ts # Handles slash commands (/ping)
├── constants.ts         # Centralized ports, URLs, intervals, and endpoints
├── types/               # TypeScript models & sync payloads
├── index.ts             # Entrypoint bootstrapping the Discord client & HTTP server
└── placeholder.test.ts  # Vitest test suite
```

## Lifecycle & Integration

1. **Startup**: the Discord client and the internal HTTP server (`GET /health`, `POST /leave/:guildId`) both start unconditionally, even without a real `DISCORD_BOT_TOKEN` — only the Discord gateway login is conditional on a real token, see [Notes](#notes) below.
2. **OAuth Bot Invitation**: When invited to a new guild, `guildCreate` fires and automatically registers/syncs the server with the backend (`POST /api/discord/bot/sync`).
3. **Periodic Sync**: Every 60 seconds, `syncAllGuilds` syncs member count and guild info with the backend.
4. **CRM Disconnect**: When disconnected via the Angora UI, the backend sends a request to the bot's internal HTTP server (`POST /leave/:guildId`), prompting the bot to leave the Discord guild.
5. **Discord-Side Removal**: If kicked or removed directly within Discord, `guildDelete` notifies the backend to update `botJoined: false`.

## Notes

- `package.json` declares `"type": "module"` — don't remove it.
- Not running / not doing anything visible: check `docker-compose logs discord-bot`.
- **Without a real `DISCORD_BOT_TOKEN`** (the default), the bot stays in a passive state — no Discord gateway connection, no guilds in cache — but the internal HTTP server on `:3001` still starts, so `GET /health` still returns `200` (this is what `docker-compose.yml`'s healthcheck relies on) and `POST /leave/:guildId` is technically reachable but a no-op, since there's no logged-in client and thus no guild ever in cache.
