export const BOT_CONFIG = {
  DEFAULT_PORT: 3001,
  DEFAULT_BACKEND_URL: 'http://backend:8080',
  DEFAULT_CLIENT_ID: '123456789012345678',
  TOKEN_PLACEHOLDER: 'YOUR_DISCORD_BOT_TOKEN',
  SYNC_INTERVAL_MS: 60000,
  REST_API_VERSION: '10',
  /** Env var holding the bot's service token for authenticating to the backend. */
  SERVICE_TOKEN_ENV: 'SERVICE_TOKEN_DISCORD_BOT',
  BACKEND_URL_ENV: 'BACKEND_URL',
  BOT_TOKEN_ENV: 'DISCORD_BOT_TOKEN',
  CLIENT_ID_ENV: 'DISCORD_CLIENT_ID',
} as const

export const BOT_ROUTES = {
  BACKEND_SYNC_ENDPOINT: '/api/discord/bot/sync',
  INTERNAL_LEAVE_PREFIX: '/leave/',
  HEALTH_PATH: '/health',
} as const
