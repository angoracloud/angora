export const API_ENDPOINTS = {
  HEALTH: '/api/health',
  DISCORD_SERVERS: '/api/discord/servers',
  DISCORD_BOT_INVITE: '/api/discord/bot/invite',
  DISCORD_SERVER_BY_ID: (id: string) => `/api/discord/servers/${id}`,
} as const

export const DISCORD_CONFIG = {
  DEFAULT_CLIENT_ID: '123456789012345678',
  DEFAULT_PERMISSIONS: '2147568640',
  OAUTH_SCOPES: 'bot+applications.commands',
  OAUTH_AUTHORIZE_BASE_URL: 'https://discord.com/oauth2/authorize',
  FALLBACK_INVITE_URL:
    'https://discord.com/oauth2/authorize?client_id=123456789012345678&scope=bot+applications.commands&permissions=2147568640',
} as const

export const TIMING_CONFIG = {
  BACKGROUND_POLL_INTERVAL_MS: 2500,
  TOAST_AUTO_DISMISS_MS: 5000,
} as const

export const CONFIRM_MESSAGES = {
  LEAVE_SERVER: (serverName?: string) =>
    `Are you sure you want to disconnect ${serverName || 'this Discord server'}?`,
} as const

export const TOAST_MESSAGES = {
  BOT_LEFT_SERVER: (serverName?: string) => ({
    title: 'Bot Left Server',
    message: `Angora Bot has left "${serverName || 'a Discord server'}".`,
  }),
  SERVER_DISCONNECT_FAILED: (serverName?: string, reason?: string) => ({
    title: 'Disconnect Failed',
    message: `Could not disconnect ${serverName || 'server'} (${reason || 'Network error'}).`,
  }),
} as const
