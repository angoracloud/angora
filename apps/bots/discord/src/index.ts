import { BOT_CONFIG } from './constants.js'
import {
  createDiscordClient,
  setupClientListeners,
} from './client/discordClient.js'
import { startInternalHttpServer } from './server/internalHttpServer.js'

const token = process.env.DISCORD_BOT_TOKEN
const clientId = process.env.DISCORD_CLIENT_ID

// Initialize Discord Gateway Client & Internal HTTP Control Server.
// Both start unconditionally so the container has a listening health
// endpoint regardless of whether real Discord credentials are configured.
const client = createDiscordClient()
startInternalHttpServer(client, BOT_CONFIG.DEFAULT_PORT)

// Check if credentials are configured
if (!token || token === BOT_CONFIG.TOKEN_PLACEHOLDER) {
  console.log('[Discord Bot] No DISCORD_BOT_TOKEN provided in environment.')
  console.log(
    '[Discord Bot] Set DISCORD_BOT_TOKEN and DISCORD_CLIENT_ID in .env to connect to live Discord gateway.',
  )
  console.log('[Discord Bot] Standing by in passive state...')
} else {
  setupClientListeners(client, token, clientId)

  // Connect to Discord Gateway
  client.login(token).catch((err: Error) => {
    console.error('[Discord Bot] Login failed:', err.message)
  })
}
