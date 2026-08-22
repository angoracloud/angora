import http from 'node:http'
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
  console.log('Slack Bot ready')
})
