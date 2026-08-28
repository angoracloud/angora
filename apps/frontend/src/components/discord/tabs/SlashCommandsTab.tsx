import { Card, Pill } from '../../ui'
import { SLASH_COMMANDS_COPY } from '../../../copy'

export function SlashCommandsTab() {
  return (
    <Card>
      <Card.Header title={SLASH_COMMANDS_COPY.OVERVIEW_TITLE} />
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-6)',
        }}
      >
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: 'var(--space-6) var(--space-7)',
            border: '0.0625rem solid var(--color-border)',
            borderRadius: 'var(--radius-control)',
          }}
        >
          <div>
            <div
              style={{
                fontFamily: 'var(--font-mono)',
                fontWeight: 'var(--font-weight-semibold)',
                color: 'var(--color-navy)',
              }}
            >
              {SLASH_COMMANDS_COPY.PING_NAME}
            </div>
            <div
              style={{
                fontSize: 'var(--font-size-xs)',
                color: 'var(--color-text-secondary)',
                marginTop: 'var(--space-1)',
              }}
            >
              {SLASH_COMMANDS_COPY.PING_DESCRIPTION}
            </div>
          </div>
          <Pill variant="positive">{SLASH_COMMANDS_COPY.PING_STATUS}</Pill>
        </div>

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: 'var(--space-6) var(--space-7)',
            border: '0.0625rem dashed var(--color-border)',
            borderRadius: 'var(--radius-control)',
            opacity: 0.7,
          }}
        >
          <div>
            <div
              style={{
                fontFamily: 'var(--font-mono)',
                fontWeight: 'var(--font-weight-semibold)',
                color: 'var(--color-navy)',
              }}
            >
              {SLASH_COMMANDS_COPY.DEDICATED_NAME}
            </div>
            <div
              style={{
                fontSize: 'var(--font-size-xs)',
                color: 'var(--color-text-secondary)',
                marginTop: 'var(--space-1)',
              }}
            >
              {SLASH_COMMANDS_COPY.DEDICATED_DESCRIPTION}
            </div>
          </div>
          <Pill variant="info">{SLASH_COMMANDS_COPY.DEDICATED_STATUS}</Pill>
        </div>
      </div>
    </Card>
  )
}
