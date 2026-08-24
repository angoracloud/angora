import { lazy, Suspense, useState } from 'react'
import { Outlet, useLocation } from '@tanstack/react-router'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'
import { useDiscordServersQuery } from '../../hooks/discordQueries'
import { ROUTES } from '../../routes'
import styles from './AppShell.module.css'

const TanStackRouterDevtools = lazy(() =>
  import('@tanstack/react-router-devtools').then((m) => ({
    default: m.TanStackRouterDevtools,
  })),
)

export function AppShell() {
  const location = useLocation()
  const mode = location.pathname.startsWith(ROUTES.SETTINGS)
    ? 'settings'
    : 'main'
  const [mobileNavOpen, setMobileNavOpen] = useState(false)
  const { data: servers } = useDiscordServersQuery()

  // Reset on route change; adjusted during render, not in an effect — see
  // https://react.dev/learn/you-might-not-need-an-effect#adjusting-some-state-when-a-prop-changes
  const [prevPathname, setPrevPathname] = useState(location.pathname)
  if (location.pathname !== prevPathname) {
    setPrevPathname(location.pathname)
    setMobileNavOpen(false)
  }

  return (
    <div className={styles.shell}>
      <Sidebar
        mode={mode}
        open={mobileNavOpen}
        counts={{ discordServers: servers?.length ?? 0 }}
      />
      {mobileNavOpen && (
        <div
          className={styles.backdrop}
          onClick={() => setMobileNavOpen(false)}
          aria-hidden="true"
        />
      )}
      <div className={styles.main}>
        <TopBar onMenuClick={() => setMobileNavOpen((open) => !open)} />
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
      {import.meta.env.DEV && (
        <Suspense fallback={null}>
          <TanStackRouterDevtools />
        </Suspense>
      )}
    </div>
  )
}
