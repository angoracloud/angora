import {
  CONFIRM_MESSAGES,
  DEFAULT_ERROR_REASON,
  DISCORD_CONFIG,
  TOAST_MESSAGES,
} from '../../../constants'
import { CONNECTED_SERVERS_STRINGS } from '../../../strings'
import {
  useDeleteServerMutation,
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
  const deleteMutation = useDeleteServerMutation()
  const { addToast } = useToast()
  const inviteUrl = inviteData?.inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  function handleLeave(id: string, serverName?: string) {
    if (!confirm(CONFIRM_MESSAGES.LEAVE_SERVER(serverName))) {
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
          const message =
            err instanceof Error ? err.message : DEFAULT_ERROR_REASON
          const toastData = TOAST_MESSAGES.SERVER_DISCONNECT_FAILED(
            serverName,
            message,
          )
          addToast('error', toastData.title, toastData.message)
        },
      },
    )
  }

  function handleDelete(id: string, serverName?: string) {
    if (!confirm(CONFIRM_MESSAGES.DELETE_SERVER(serverName))) {
      return
    }

    deleteMutation.mutate(
      { id, serverName },
      {
        onSuccess: () => {
          const toastData = TOAST_MESSAGES.SERVER_DELETED(serverName)
          addToast('success', toastData.title, toastData.message)
        },
        onError: (err) => {
          const message =
            err instanceof Error ? err.message : DEFAULT_ERROR_REASON
          const toastData = TOAST_MESSAGES.SERVER_DELETE_FAILED(
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
          {CONNECTED_SERVERS_STRINGS.LOADING}
        </p>
      )}

      {error && (
        <p
          style={{
            color: 'var(--color-danger-text)',
            padding: 'var(--space-6) 0',
          }}
        >
          {CONNECTED_SERVERS_STRINGS.ERROR_PREFIX} {error.message}
        </p>
      )}

      {!isLoading && servers.length === 0 && (
        <Card>
          <div style={{ textAlign: 'center', padding: 'var(--space-9) 0' }}>
            <h3>{CONNECTED_SERVERS_STRINGS.EMPTY_TITLE}</h3>
            <p
              style={{
                color: 'var(--color-text-secondary)',
                margin: 'var(--space-3) 0 var(--space-7)',
              }}
            >
              {CONNECTED_SERVERS_STRINGS.EMPTY_BODY}
            </p>
            <LinkButton
              href={inviteUrl}
              target="_blank"
              rel="noreferrer"
              variant="primary"
            >
              {CONNECTED_SERVERS_STRINGS.EMPTY_CTA}
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
            {CONNECTED_SERVERS_STRINGS.REGISTERED_COUNT(servers.length)}
          </span>
          <Pill variant="positive">{CONNECTED_SERVERS_STRINGS.LIVE_SYNC}</Pill>
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
            onDelete={handleDelete}
          />
        ))}
      </div>
    </div>
  )
}
