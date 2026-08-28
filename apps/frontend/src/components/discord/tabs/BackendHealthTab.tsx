import { useDiscordInviteQuery } from '../../../hooks/discordQueries'
import { BACKEND_HEALTH_COPY } from '../../../copy'
import { Card } from '../../ui'

export function BackendHealthTab() {
  const { data: inviteData } = useDiscordInviteQuery()

  return (
    <Card>
      <Card.Header title={BACKEND_HEALTH_COPY.TITLE} />
      <pre
        style={{
          background: 'var(--color-canvas)',
          padding: 'var(--space-6)',
          borderRadius: 'var(--radius-control)',
          overflowX: 'auto',
          color: 'var(--color-text-secondary)',
          fontFamily: 'var(--font-mono)',
          fontSize: 'var(--font-size-sm)',
        }}
      >
        {JSON.stringify(inviteData, null, 2)}
      </pre>
    </Card>
  )
}
