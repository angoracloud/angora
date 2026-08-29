import http from 'node:http'
import { loadSecrets } from '@angora/secrets'
import { BOT_CONFIG, BOT_ROUTES } from './constants.js'

const server = http.createServer((req, res) => {
  if (
    (req.method === 'GET' || req.method === 'HEAD') &&
    req.url === BOT_ROUTES.HEALTH_PATH
  ) {
    res.writeHead(200, { 'Content-Type': 'application/json' })
    res.end(
      req.method === 'HEAD' ? undefined : JSON.stringify({ status: 'ok' }),
    )
    return
  }
  res.writeHead(404, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify({ error: 'Not found' }))
})

server.listen(BOT_CONFIG.DEFAULT_PORT, () => {
  console.log('Email Bot ready')
})

// This bot consumes no secrets of its own yet, so nothing here reads from the
// result. Resolving them anyway keeps every service on the same startup contract:
// if INFISICAL_ENABLED is set but misconfigured, this bot fails loudly at boot
// alongside the others rather than looking healthy while the stack is broken.
await loadSecrets().catch((err: unknown) => {
  console.error('[Email Bot] Could not load secrets:', err)
  process.exit(1)
})
