import type { LucideIcon } from 'lucide-react'
import { Home, MessageSquare, X } from 'lucide-react'
import { ROUTES } from '../../routes'
import { NAV_COPY, PAGE_TITLES } from '../../copy'

export interface NavItem {
  label: string
  path: string
  icon: LucideIcon
  end?: boolean
  countKey?: string
}

export interface NavSection {
  section: string
  items: NavItem[]
}

export const MAIN_NAV: NavSection[] = [
  {
    section: PAGE_TITLES.OVERVIEW,
    items: [{ label: NAV_COPY.HOME, path: ROUTES.HOME, icon: Home, end: true }],
  },
  {
    section: NAV_COPY.SECTION_INTEGRATIONS,
    items: [
      {
        label: PAGE_TITLES.DISCORD_BOT,
        path: ROUTES.DISCORD.ROOT,
        icon: MessageSquare,
        countKey: 'discordServers',
      },
    ],
  },
]

export const SETTINGS_NAV: NavSection[] = [
  {
    section: PAGE_TITLES.SETTINGS,
    items: [{ label: NAV_COPY.EXIT_SETTINGS, path: ROUTES.HOME, icon: X }],
  },
]
