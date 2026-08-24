import { DISCORD_CONFIG, TOAST_MESSAGES } from '../../../constants'
import {
  useDiscordInviteQuery,
  useDiscordServersQuery,
  useLeaveServerMutation,
} from '../../../hooks/discordQueries'
import { useToast } from '../../../hooks/useToast'
import { Card, LinkButton, Pill } from '../../ui'
import { ServerCard } from './ServerCard'

export function ConnectedServersTab() {
  const { data: servers = [], isLoading, error } = useDiscordServersQuery()
  const { data: inviteData } = useDiscordInviteQuery()
  const leaveMutation = useLeaveServerMutation()
  const { addToast } = useToast()
  const inviteUrl = inviteData?.inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  function handleLeave(id: string, serverName?: string) {
    if (
      !confirm(
        `Are you sure you want to disconnect ${serverName || 'this Discord server'}?`,
      )
    ) {
      return
    }

    leaveMutation.mutate(
      { id, serverName },
      {
        onSuccess: () => {
          const toastData = TOAST_MESSAGES.BOT_LEFT_SERVER(serverName)
          addToast('info', toastData.title, toastData.message)
        },
        onError: (err) => {
          const message = err instanceof Error ? err.message : 'Network error'
          const toastData = TOAST_MESSAGES.SERVER_DISCONNECT_FAILED(
            serverName,
            message,
          )
          addToast('error', toastData.title, toastData.message)
        },
      },
    )
  }

  return (
    <div>
      {isLoading && (
        <p
          style={{
            color: 'var(--color-text-secondary)',
            padding: 'var(--space-6) 0',
          }}
        >
          Loading connected servers...
        </p>
      )}

      {error && (
        <p
          style={{
            color: 'var(--color-danger-text)',
            padding: 'var(--space-6) 0',
          }}
        >
          Error loading servers: {error.message}
        </p>
      )}

      {!isLoading && servers.length === 0 && (
        <Card>
          <div style={{ textAlign: 'center', padding: 'var(--space-9) 0' }}>
            <h3>No Discord Servers Connected</h3>
            <p
              style={{
                color: 'var(--color-text-secondary)',
                margin: 'var(--space-3) 0 var(--space-7)',
              }}
            >
              Invite the bot to your Discord server to get started.
            </p>
            <LinkButton
              href={inviteUrl}
              target="_blank"
              rel="noreferrer"
              variant="primary"
            >
              Invite Bot to Discord Server (OAuth)
            </LinkButton>
          </div>
        </Card>
      )}

      {!isLoading && servers.length > 0 && (
        <div
          style={{
            display: 'flex',
            flexWrap: 'wrap',
            justifyContent: 'space-between',
            alignItems: 'center',
            gap: 'var(--space-3)',
            marginBottom: 'var(--space-7)',
            padding: 'var(--space-3) var(--space-5)',
            background: 'var(--color-surface)',
            borderRadius: 'var(--radius-control)',
            border: '0.0625rem solid var(--color-border)',
          }}
        >
          <span
            style={{
              fontSize: 'var(--font-size-sm)',
              color: 'var(--color-text-secondary)',
              fontWeight: 'var(--font-weight-medium)',
            }}
          >
            {servers.length} server{servers.length === 1 ? '' : 's'} registered
          </span>
          <Pill variant="positive">Live auto-sync active</Pill>
        </div>
      )}

      <div
        style={{
          display: 'grid',
          gridTemplateColumns:
            'repeat(auto-fill, minmax(min(20rem, 100%), 1fr))',
          gap: 'var(--space-8)',
        }}
      >
        {servers.map((server) => (
          <ServerCard
            key={server.id}
            server={server}
            inviteUrl={inviteData?.inviteUrl}
            onLeave={handleLeave}
          />
        ))}
      </div>
    </div>
  )
}
