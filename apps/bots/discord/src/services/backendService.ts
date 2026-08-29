import type { Client } from 'discord.js'
import type { SecretsProvider } from '@angora/secrets'
import { BOT_CONFIG, BOT_ROUTES } from '../constants.js'
import type { GuildSyncPayload } from '../types/index.js'

// Explicitly `string`: BOT_CONFIG is `as const`, so the initializer would
// otherwise narrow this to the default URL's literal type.
let backendUrl: string = BOT_CONFIG.DEFAULT_BACKEND_URL

/**
 * Service token authenticating this bot to the backend. The backend registers the
 * matching hash at startup from the same value, so the two must agree.
 */
let serviceToken: string | undefined

/**
 * Whether `configureBackendService` has run. The internal HTTP server starts
 * listening before `loadSecrets()` resolves — deliberately, so the healthcheck
 * has an endpoint during the Infisical fetch — which leaves a window where a
 * request could reach this module before the token is bound.
 */
let configured = false

/**
 * Binds this module to the secrets resolved at startup.
 *
 * These used to be module-level constants read straight from `process.env` at
 * import time. They can't be any more: with Infisical enabled the values aren't
 * known until after the async fetch in `loadSecrets()` completes.
 */
export function configureBackendService(secrets: SecretsProvider): void {
  backendUrl = secrets.get(
    BOT_CONFIG.BACKEND_URL_ENV,
    BOT_CONFIG.DEFAULT_BACKEND_URL,
  )
  serviceToken = secrets.get(BOT_CONFIG.SERVICE_TOKEN_ENV)
  configured = true
}

/**
 * Synchronizes an individual Discord guild's live state with the Angora backend.
 */
export async function syncGuildWithBackend(
  guildData: GuildSyncPayload,
): Promise<void> {
  // Refuse rather than sync unauthenticated: without the token the backend
  // answers 401, which reads as a token mismatch instead of a startup race.
  if (!configured) {
    console.error(
      `[Discord Bot] Skipped syncing guild ${guildData.name} — secrets are not resolved yet`,
    )
    return
  }

  try {
    const res = await fetch(
      `${backendUrl}${BOT_ROUTES.BACKEND_SYNC_ENDPOINT}`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(serviceToken ? { Authorization: `Bearer ${serviceToken}` } : {}),
        },
        body: JSON.stringify(guildData),
      },
    )
    if (!res.ok) {
      const hint =
        res.status === 401
          ? ` — check ${BOT_CONFIG.SERVICE_TOKEN_ENV} matches the backend's`
          : ''
      console.error(
        `[Discord Bot] Failed to sync guild ${guildData.name} (${res.status})${hint}`,
      )
    } else {
      console.log(
        `[Discord Bot] Successfully synced server: ${guildData.name} (${guildData.guildId})`,
      )
    }
  } catch (err) {
    console.error(`[Discord Bot] Backend connection error during sync:`, err)
  }
}

/**
 * Iterates over all Discord guilds cached by the client and reconciles them with the backend.
 */
export async function syncAllGuilds(client: Client): Promise<void> {
  for (const [, guild] of client.guilds.cache) {
    await syncGuildWithBackend({
      guildId: guild.id,
      name: guild.name,
      iconUrl: guild.iconURL(),
      ownerId: guild.ownerId,
      memberCount: guild.memberCount,
      botJoined: true,
    })
  }
}
