import {
  createRootRoute,
  createRoute,
  createRouter,
  redirect,
} from '@tanstack/react-router'
import { AppShell } from './components/layout/AppShell'
import { HomePage } from './components/home/HomePage'
import { DiscordPage } from './components/discord/DiscordPage'
import { ConnectedServersTab } from './components/discord/tabs/ConnectedServersTab'
import { SlashCommandsTab } from './components/discord/tabs/SlashCommandsTab'
import { BackendHealthTab } from './components/discord/tabs/BackendHealthTab'
import { SettingsPage } from './components/settings/SettingsPage'
import { NotFoundPage } from './components/NotFoundPage'
import { ROUTE_SEGMENTS, ROUTES } from './routes'

declare module '@tanstack/react-router' {
  interface StaticDataRouteOption {
    title?: string
    crumbs?: { label: string; path?: string }[]
  }
}

const rootRoute = createRootRoute({
  component: AppShell,
})

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: ROUTES.HOME,
  component: HomePage,
  staticData: { title: 'Overview' },
})

const discordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: ROUTE_SEGMENTS.DISCORDBOT,
  component: DiscordPage,
  staticData: { title: 'Discord Bot' },
})

const discordIndexRoute = createRoute({
  getParentRoute: () => discordRoute,
  path: '/',
  beforeLoad: () => {
    throw redirect({ to: ROUTES.DISCORD.SERVERS })
  },
})

const discordServersRoute = createRoute({
  getParentRoute: () => discordRoute,
  path: ROUTE_SEGMENTS.SERVERS,
  component: ConnectedServersTab,
})

const discordCommandsRoute = createRoute({
  getParentRoute: () => discordRoute,
  path: ROUTE_SEGMENTS.COMMANDS,
  component: SlashCommandsTab,
})

const discordHealthRoute = createRoute({
  getParentRoute: () => discordRoute,
  path: ROUTE_SEGMENTS.HEALTH,
  component: BackendHealthTab,
})

const settingsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: ROUTE_SEGMENTS.SETTINGS,
  component: SettingsPage,
  staticData: { title: 'Settings' },
})

const routeTree = rootRoute.addChildren([
  homeRoute,
  discordRoute.addChildren([
    discordIndexRoute,
    discordServersRoute,
    discordCommandsRoute,
    discordHealthRoute,
  ]),
  settingsRoute,
])

export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: NotFoundPage,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
