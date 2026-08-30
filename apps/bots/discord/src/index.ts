import { loadSecrets } from '@angora/secrets'
import { BOT_CONFIG } from './constants.js'
import {
  createDiscordClient,
  setupClientListeners,
} from './client/discordClient.js'
import { startInternalHttpServer } from './server/internalHttpServer.js'
import { configureBackendService } from './services/backendService.js'

// Both start unconditionally, and before secrets resolve, so the health endpoint
// is listening for docker-compose's healthcheck even without real credentials.
const client = createDiscordClient()
startInternalHttpServer(client, BOT_CONFIG.DEFAULT_PORT)

// Fatal on purpose: continuing would run on whatever placeholder credentials
// happen to be in the environment.
const secrets = await loadSecrets().catch((err: unknown) => {
  console.error('[Discord Bot] Could not load secrets:', err)
  process.exit(1)
})

configureBackendService(secrets)

const token = secrets.get(BOT_CONFIG.BOT_TOKEN_ENV)
const clientId = secrets.get(BOT_CONFIG.CLIENT_ID_ENV)

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
