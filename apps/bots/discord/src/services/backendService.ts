import type { Client } from 'discord.js'
import { BOT_CONFIG, BOT_ROUTES } from '../constants.js'
import type { GuildSyncPayload } from '../types/index.js'

const backendUrl = process.env.BACKEND_URL || BOT_CONFIG.DEFAULT_BACKEND_URL

/**
 * Service token authenticating this bot to the backend. The backend registers the
 * matching hash at startup from the same value, so the two must agree.
 */
const serviceToken = process.env[BOT_CONFIG.SERVICE_TOKEN_ENV]

/**
 * Synchronizes an individual Discord guild's live state with the Angora backend.
 */
export async function syncGuildWithBackend(
  guildData: GuildSyncPayload,
): Promise<void> {
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
