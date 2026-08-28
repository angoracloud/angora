// Route segments — the single source of truth for path literals. ROUTES
// (below) composes these into full paths for Link/navigate/redirect; router.tsx
// uses the bare segments directly, since createRoute's `path` for a non-root
// route is relative to its parent (a full path there would double-prefix,
// e.g. '/discordbot' + '/discordbot/servers' -> '/discordbot/discordbot/servers').
const DISCORDBOT = 'discordbot'
const SERVERS = 'servers'
const COMMANDS = 'commands'
const HEALTH = 'health'
const SETTINGS = 'settings'

export const ROUTE_SEGMENTS = {
  DISCORDBOT,
  SERVERS,
  COMMANDS,
  HEALTH,
  SETTINGS,
} as const

export const ROUTES = {
  HOME: '/',
  DISCORD: {
    ROOT: `/${DISCORDBOT}`,
    SERVERS: `/${DISCORDBOT}/${SERVERS}`,
    COMMANDS: `/${DISCORDBOT}/${COMMANDS}`,
    HEALTH: `/${DISCORDBOT}/${HEALTH}`,
  },
  SETTINGS: `/${SETTINGS}`,
} as const
