// Centralized user-facing UI text. Config-like values (API paths, timing,
// external IDs) live in constants.ts instead — this file is copy only. See
// apps/frontend/AGENTS.md's constants-discipline note for what belongs here.

// Shared across router.tsx (route staticData.title), navConfig.ts (nav item/
// section labels), SettingsPage.tsx, and TopBar.tsx's settings icon button —
// each currently uses the exact same word for the exact same page, so they
// share one constant instead of four independently-typed copies.
export const PAGE_TITLES = {
  OVERVIEW: 'Overview',
  DISCORD_BOT: 'Discord Bot',
  SETTINGS: 'Settings',
} as const

export const NAV_STRINGS = {
  SECTION_INTEGRATIONS: 'Integrations',
  HOME: 'Home',
  EXIT_SETTINGS: 'Exit Settings',
} as const

export const SIDEBAR_STRINGS = {
  WORDMARK: 'Angora',
  TAG_ADMIN: 'ADMIN',
  TAG_MAIN: 'CRM',
  VERSION_SUFFIX: 'self-hosted',
} as const

export const TOPBAR_STRINGS = {
  TOGGLE_NAV: 'Toggle navigation',
  SEARCH_PLACEHOLDER: 'Search tickets, contacts, messages…',
  NOTIFICATIONS: 'Notifications',
} as const

export const TOAST_CONTAINER_STRINGS = {
  REGION_LABEL: 'Notifications',
  CLOSE_LABEL: 'Close notification',
} as const

export const NOT_FOUND_STRINGS = {
  RETURN_HOME: 'Return home',
} as const

export const SETTINGS_PAGE_STRINGS = {
  BODY: 'Workspace settings are not implemented yet.',
} as const

export const HOME_PAGE_STRINGS = {
  DISCORD: {
    STATUS: 'Active',
    TITLE: 'Discord Bot Integration',
    DESCRIPTION:
      'View active Discord servers, manage bot OAuth invitation links, track member stats, and execute slash commands.',
    CTA: 'Open Discord Manager',
  },
  SLACK: {
    STATUS: 'Configured',
    TITLE: 'Slack Workspace Bot',
    DESCRIPTION:
      'Connect support agents with customer support channels, receive ticket updates, and automate workspace notifications.',
    CTA: 'Slack Engine Ready',
  },
  EMAIL: {
    STATUS: 'Configured',
    TITLE: 'Email Ticket System',
    DESCRIPTION:
      'Inbound IMAP/SMTP message listener for automatic ticket generation, response dispatching, and conversation logs.',
    CTA: 'Email Engine Ready',
  },
  POSTGRES: {
    STATUS: 'Connected',
    TITLE: 'PostgreSQL Database',
    DESCRIPTION:
      'KTor Exposed ORM engine powered by PostgreSQL with Flyway automated migrations.',
    CTA: 'View Connected Records',
  },
} as const

export const DISCORD_PAGE_STRINGS = {
  DESCRIPTION:
    'Manage connected Discord servers, invite Angora Bot, and view slash commands.',
  INVITE_CTA: 'Add Bot to Server (OAuth)',
  TAB_SERVERS: 'Connected Servers',
  TAB_COMMANDS: 'Slash Commands',
  TAB_HEALTH: 'Backend Health',
} as const

export const CONNECTED_SERVERS_STRINGS = {
  LOADING: 'Loading connected servers...',
  ERROR_PREFIX: 'Error loading servers:',
  EMPTY_TITLE: 'No Discord Servers Connected',
  EMPTY_BODY: 'Invite the bot to your Discord server to get started.',
  EMPTY_CTA: 'Invite Bot to Discord Server (OAuth)',
  REGISTERED_COUNT: (count: number) =>
    `${count} server${count === 1 ? '' : 's'} registered`,
  LIVE_SYNC: 'Live auto-sync active',
} as const

export const SERVER_CARD_STRINGS = {
  ID_LABEL: 'ID:',
  MEMBERS_LABEL: 'Members:',
  CONNECTED: 'Bot Connected',
  DISCONNECTED: 'Bot Left',
  REMOVE: 'Remove',
  RECONNECT: 'Reconnect',
} as const

export const SLASH_COMMANDS_STRINGS = {
  OVERVIEW_TITLE: 'Command Registry Overview',
  PING_NAME: '/ping',
  PING_DESCRIPTION: 'Checks bot WebSocket latency & API roundtrip latency',
  PING_STATUS: 'Registered',
  DEDICATED_NAME: '/angora (Dedicated)',
  DEDICATED_DESCRIPTION: 'Placeholder slot ready for custom dedicated commands',
  DEDICATED_STATUS: 'Ready for implementation',
} as const

export const BACKEND_HEALTH_STRINGS = {
  TITLE: 'Discord OAuth Invite Link Data',
} as const
