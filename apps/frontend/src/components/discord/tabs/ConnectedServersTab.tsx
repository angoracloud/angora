import { DISCORD_CONFIG } from '../../../constants'
import type { DiscordServer, InviteData } from '../../../types'
import { ServerCard } from './ServerCard'

interface ConnectedServersTabProps {
  servers: DiscordServer[]
  inviteData: InviteData | null
  loading: boolean
  error: string | null
  onLeaveServer: (id: string, name: string) => void
  onDeleteServer: (id: string, name: string) => void
}

export function ConnectedServersTab({
  servers,
  inviteData,
  loading,
  error,
  onLeaveServer,
  onDeleteServer,
}: ConnectedServersTabProps) {
  const inviteUrl = inviteData?.inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  return (
    <div>
      {loading && (
        <p style={{ color: 'var(--text-secondary)', padding: '1rem 0' }}>
          Loading connected servers...
        </p>
      )}

      {error && (
        <p style={{ color: 'var(--danger)', padding: '1rem 0' }}>
          Error loading servers: {error}
        </p>
      )}

      {!loading && servers.length === 0 && (
        <div className="empty-state">
          <div className="empty-icon">🤖</div>
          <h3>No Discord Servers Connected</h3>
          <p
            style={{
              color: 'var(--text-secondary)',
              marginBottom: '1.5rem',
              marginTop: '0.5rem',
            }}
          >
            Invite the bot to your Discord server to get started.
          </p>
          <a
            href={inviteUrl}
            target="_blank"
            rel="noreferrer"
            className="btn btn-discord"
          >
            🤖 Invite Bot to Discord Server (OAuth)
          </a>
        </div>
      )}

      {!loading && servers.length > 0 && (
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: '1.25rem',
            padding: '0.5rem 0.75rem',
            background: 'rgba(255, 255, 255, 0.02)',
            borderRadius: 'var(--radius-sm)',
            border: '1px solid var(--border-color)',
          }}
        >
          <span
            style={{
              fontSize: '0.85rem',
              color: 'var(--text-secondary)',
              fontWeight: 500,
            }}
          >
            📋 {servers.length} server{servers.length === 1 ? '' : 's'}{' '}
            registered
          </span>
          <span
            className="status-badge active"
            style={{ fontSize: '0.75rem', padding: '0.2rem 0.6rem' }}
          >
            <span className="status-dot"></span> Live auto-sync active
          </span>
        </div>
      )}

      <div className="grid-container">
        {servers.map((server) => (
          <ServerCard
            key={server.id}
            server={server}
            inviteUrl={inviteData?.inviteUrl}
            onLeave={onLeaveServer}
            onDelete={onDeleteServer}
          />
        ))}
      </div>
    </div>
  )
}
