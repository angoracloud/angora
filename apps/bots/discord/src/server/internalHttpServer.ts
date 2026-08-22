import http from 'node:http'
import type { Client } from 'discord.js'
import { BOT_CONFIG, BOT_ROUTES } from '../constants.js'
import { syncGuildWithBackend } from '../services/backendService.js'

/**
 * Starts the internal HTTP listener for CRM control actions (e.g., bot leaving a guild).
 */
export function startInternalHttpServer(
  client: Client,
  port: number = BOT_CONFIG.DEFAULT_PORT,
): http.Server {
  const server = http.createServer(async (req, res) => {
    const url = req.url || ''

    if (
      (req.method === 'GET' || req.method === 'HEAD') &&
      url === BOT_ROUTES.HEALTH_PATH
    ) {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.end(
        req.method === 'HEAD' ? undefined : JSON.stringify({ status: 'ok' }),
      )
      return
    }

    if (
      req.method === 'POST' &&
      url.startsWith(BOT_ROUTES.INTERNAL_LEAVE_PREFIX)
    ) {
      const targetGuildId = url.split(BOT_ROUTES.INTERNAL_LEAVE_PREFIX)[1]
      if (targetGuildId) {
        const guild = client.guilds.cache.get(targetGuildId)
        if (guild) {
          try {
            console.log(
              `[Discord Bot] Leaving server: ${guild.name} (${targetGuildId}) per CRM request`,
            )
            await guild.leave()
            await syncGuildWithBackend({
              guildId: targetGuildId,
              name: guild.name,
              memberCount: guild.memberCount,
              botJoined: false,
            })
            res.writeHead(200, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ status: 'left', guildId: targetGuildId }))
            return
          } catch (err) {
            console.error(
              `[Discord Bot] Error leaving guild ${targetGuildId}:`,
              err,
            )
            res.writeHead(500, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ error: 'Failed to leave guild' }))
            return
          }
        } else {
          // Guild is already not in the bot's cache (e.g. left earlier or kicked from Discord)
          res.writeHead(200, { 'Content-Type': 'application/json' })
          res.end(
            JSON.stringify({ status: 'already_left', guildId: targetGuildId }),
          )
          return
        }
      }
    }

    res.writeHead(404, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify({ error: 'Not found' }))
  })

  server.listen(port, () => {
    console.log(`[Discord Bot] Internal API listener running on port ${port}`)
  })

  return server
}
