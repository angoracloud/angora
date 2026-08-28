import { Link, Outlet } from '@tanstack/react-router'
import {
  useDiscordInviteQuery,
  useDiscordServersQuery,
} from '../../hooks/discordQueries'
import { DISCORD_CONFIG } from '../../constants'
import { DISCORD_PAGE_COPY } from '../../copy'
import { ROUTES } from '../../routes'
import { CountBadge, LinkButton } from '../ui'
import tabButtonStyles from '../ui/TabButton.module.css'

export function DiscordPage() {
  const { data: servers } = useDiscordServersQuery()
  const { data: inviteData } = useDiscordInviteQuery()
  const inviteUrl = inviteData?.inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  return (
    <div>
      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 'var(--space-6)',
          marginBottom: 'var(--space-8)',
        }}
      >
        <p style={{ color: 'var(--color-text-secondary)' }}>
          {DISCORD_PAGE_COPY.DESCRIPTION}
        </p>
        <LinkButton
          href={inviteUrl}
          target="_blank"
          rel="noreferrer"
          variant="primary"
        >
          {DISCORD_PAGE_COPY.INVITE_CTA}
        </LinkButton>
      </div>

      <nav
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          gap: 'var(--space-3)',
          marginBottom: 'var(--space-8)',
        }}
      >
        <Link
          to={ROUTES.DISCORD.SERVERS}
          className={tabButtonStyles.tabBtn}
          activeProps={{ className: tabButtonStyles.active }}
        >
          {({ isActive }) => (
            <>
              {DISCORD_PAGE_COPY.TAB_SERVERS}{' '}
              <CountBadge count={servers?.length ?? 0} active={isActive} />
            </>
          )}
        </Link>
        <Link
          to={ROUTES.DISCORD.COMMANDS}
          className={tabButtonStyles.tabBtn}
          activeProps={{ className: tabButtonStyles.active }}
        >
          {DISCORD_PAGE_COPY.TAB_COMMANDS}
        </Link>
        <Link
          to={ROUTES.DISCORD.HEALTH}
          className={tabButtonStyles.tabBtn}
          activeProps={{ className: tabButtonStyles.active }}
        >
          {DISCORD_PAGE_COPY.TAB_HEALTH}
        </Link>
      </nav>

      <Outlet />
    </div>
  )
}
