import { Bell, Menu, Settings } from 'lucide-react'
import { Link, useMatches, useNavigate } from '@tanstack/react-router'
import { Avatar } from '../ui/Avatar'
import { IconButton } from '../ui/IconButton'
import { SearchInput } from '../ui/SearchInput'
import { CURRENT_USER, NOT_FOUND_TITLE } from '../../constants'
import { PAGE_TITLES, TOPBAR_STRINGS } from '../../strings'
import { ROUTES } from '../../routes'
import styles from './TopBar.module.css'

export interface TopBarProps {
  onMenuClick?: () => void
}

export function TopBar({ onMenuClick }: TopBarProps) {
  const navigate = useNavigate()
  const matches = useMatches()

  const match = [...matches]
    .reverse()
    .find((m) => m.staticData?.title || m.staticData?.crumbs)
  // No match carries staticData for the not-found fallback (it isn't a
  // matched leaf route), so fall back to a title consistent with
  // NotFoundPage's own heading instead of rendering an empty <h1>.
  const { title = NOT_FOUND_TITLE, crumbs } = match?.staticData ?? {}

  return (
    <div className={styles.top}>
      <IconButton
        icon={Menu}
        label={TOPBAR_STRINGS.TOGGLE_NAV}
        onClick={onMenuClick}
        className={styles.menuButton}
      />

      {crumbs ? (
        <h1>
          {crumbs.map((crumb, i) => (
            <span key={crumb.label}>
              {crumb.path ? (
                <Link to={crumb.path} className={styles.crumb}>
                  {crumb.label}
                </Link>
              ) : (
                <span className={styles.crumbActive}>{crumb.label}</span>
              )}
              {i < crumbs.length - 1 && (
                <span className={styles.crumbSep}>/</span>
              )}
            </span>
          ))}
        </h1>
      ) : (
        <h1>{title}</h1>
      )}

      <div className={styles.searchSlot}>
        <SearchInput placeholder={TOPBAR_STRINGS.SEARCH_PLACEHOLDER} />
      </div>

      <div className={styles.actions}>
        <IconButton icon={Bell} label={TOPBAR_STRINGS.NOTIFICATIONS} withDot />
        <IconButton
          icon={Settings}
          label={PAGE_TITLES.SETTINGS}
          onClick={() => navigate({ to: ROUTES.SETTINGS })}
        />
        <Avatar name={CURRENT_USER.NAME} size="md" />
      </div>
    </div>
  )
}
