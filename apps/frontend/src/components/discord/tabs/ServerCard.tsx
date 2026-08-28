import { DISCORD_CONFIG } from '../../../constants'
import { SERVER_CARD_STRINGS } from '../../../strings'
import type { DiscordServer } from '../../../types'
import { Avatar, Button, Card, LinkButton, StatusDot } from '../../ui'

interface ServerCardProps {
  server: DiscordServer
  inviteUrl?: string
  onLeave: (id: string, name: string) => void
}

export function ServerCard({ server, inviteUrl, onLeave }: ServerCardProps) {
  const isConnected = server.botJoined !== false
  const fallbackUrl = inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  return (
    <Card>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'var(--space-6)',
          marginBottom: 'var(--space-6)',
        }}
      >
        <Avatar name={server.name} imageUrl={server.iconUrl} size="lg" />
        <div>
          <div
            style={{
              fontSize: 'var(--font-size-lg)',
              fontWeight: 'var(--font-weight-semibold)',
              color: 'var(--color-navy)',
            }}
          >
            {server.name}
          </div>
          <div
            style={{
              fontSize: 'var(--font-size-xs)',
              color: 'var(--color-text-muted)',
              fontFamily: 'var(--font-mono)',
            }}
          >
            {SERVER_CARD_STRINGS.ID_LABEL} {server.guildId}
          </div>
        </div>
      </div>

      <div
        style={{
          fontSize: 'var(--font-size-sm)',
          color: 'var(--color-text-secondary)',
          marginBottom: 'var(--space-3)',
        }}
      >
        {SERVER_CARD_STRINGS.MEMBERS_LABEL}{' '}
        <strong>{server.memberCount}</strong>
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          paddingTop: 'var(--space-6)',
          borderTop: '0.0625rem solid var(--color-border-subtle)',
          marginTop: 'auto',
        }}
      >
        <span
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: 'var(--space-2)',
            fontSize: 'var(--font-size-xs)',
            fontWeight: 'var(--font-weight-semibold)',
            color: isConnected
              ? 'var(--color-success-text)'
              : 'var(--color-warning-text)',
          }}
        >
          <StatusDot status={isConnected ? 'solved' : 'pending'} />
          {isConnected
            ? SERVER_CARD_STRINGS.CONNECTED
            : SERVER_CARD_STRINGS.DISCONNECTED}
        </span>

        {isConnected ? (
          <Button
            variant="danger"
            size="sm"
            onClick={() => onLeave(server.id, server.name)}
          >
            {SERVER_CARD_STRINGS.REMOVE}
          </Button>
        ) : (
          <LinkButton
            href={fallbackUrl}
            target="_blank"
            rel="noreferrer"
            variant="primary"
            size="sm"
          >
            {SERVER_CARD_STRINGS.RECONNECT}
          </LinkButton>
        )}
      </div>
    </Card>
  )
}
