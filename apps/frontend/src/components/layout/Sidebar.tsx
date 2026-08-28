import { Link } from '@tanstack/react-router'
import { Avatar } from '../ui/Avatar'
import { CURRENT_USER } from '../../constants'
import { SIDEBAR_STRINGS } from '../../strings'
import { MAIN_NAV, SETTINGS_NAV } from './navConfig'
import styles from './Sidebar.module.css'

export interface SidebarProps {
  mode?: 'main' | 'settings'
  counts?: Record<string, number>
  open?: boolean
}

export function Sidebar({ mode = 'main', counts, open = false }: SidebarProps) {
  const sections = mode === 'settings' ? SETTINGS_NAV : MAIN_NAV

  return (
    <aside className={`${styles.sb}${open ? ` ${styles.open}` : ''}`}>
      <div className={styles.brand}>
        <span className={styles.brandMark}>A</span>
        <span className={styles.wordmark}>{SIDEBAR_STRINGS.WORDMARK}</span>
        <span className={styles.tag}>
          {mode === 'settings'
            ? SIDEBAR_STRINGS.TAG_ADMIN
            : SIDEBAR_STRINGS.TAG_MAIN}
        </span>
      </div>

      {sections.map((section) => (
        <div key={section.section}>
          <div className={styles.section}>{section.section}</div>
          {section.items.map((item) => {
            const count = item.countKey ? counts?.[item.countKey] : undefined
            return (
              <Link
                key={item.path}
                to={item.path}
                activeOptions={{ exact: item.end }}
                className={styles.nav}
                activeProps={{ className: styles.active }}
              >
                <item.icon size={18} />
                {item.label}
                {count !== undefined && (
                  <span className={styles.count}>{count}</span>
                )}
              </Link>
            )
          })}
        </div>
      ))}

      <div className={styles.version}>
        v{__APP_VERSION__} · {__COMMIT_HASH__} ·{' '}
        {SIDEBAR_STRINGS.VERSION_SUFFIX}
      </div>
      <div className={styles.user}>
        <Avatar name={CURRENT_USER.NAME} size="md" />
        <div>
          <div className={styles.userName}>{CURRENT_USER.NAME}</div>
          <div className={styles.userRole}>{CURRENT_USER.ROLE}</div>
        </div>
      </div>
    </aside>
  )
}
