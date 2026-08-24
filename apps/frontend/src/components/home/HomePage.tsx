import { Link } from '@tanstack/react-router'
import { Button, Card, ChannelIcon, Pill } from '../ui'
import btnStyles from '../ui/Button.module.css'
import { ROUTES } from '../../routes'

const primaryLinkClass = `${btnStyles.btn} ${btnStyles.primary} ${btnStyles.md}`
const secondaryLinkClass = `${btnStyles.btn} ${btnStyles.secondary} ${btnStyles.md}`

export function HomePage() {
  return (
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(min(20rem, 100%), 1fr))',
        gap: 'var(--space-8)',
      }}
    >
      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 'var(--space-6)',
          }}
        >
          <ChannelIcon channel="discord" />
          <Pill variant="positive">Active</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          Discord Bot Integration
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          View active Discord servers, manage bot OAuth invitation links, track
          member stats, and execute slash commands.
        </p>
        <Link
          to={ROUTES.DISCORD.SERVERS}
          className={primaryLinkClass}
          style={{ width: '100%', marginTop: 'auto' }}
        >
          Open Discord Manager
        </Link>
      </Card>

      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 'var(--space-6)',
          }}
        >
          <ChannelIcon channel="slack" />
          <Pill variant="positive">Configured</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          Slack Workspace Bot
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          Connect support agents with customer support channels, receive ticket
          updates, and automate workspace notifications.
        </p>
        <Button
          variant="secondary"
          disabled
          style={{ width: '100%', marginTop: 'auto' }}
        >
          Slack Engine Ready
        </Button>
      </Card>

      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 'var(--space-6)',
          }}
        >
          <ChannelIcon channel="email" />
          <Pill variant="positive">Configured</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          Email Ticket System
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          Inbound IMAP/SMTP message listener for automatic ticket generation,
          response dispatching, and conversation logs.
        </p>
        <Button
          variant="secondary"
          disabled
          style={{ width: '100%', marginTop: 'auto' }}
        >
          Email Engine Ready
        </Button>
      </Card>

      <Card>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: 'var(--space-6)',
          }}
        >
          <ChannelIcon channel="web" label="P" />
          <Pill variant="positive">Connected</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          PostgreSQL Database
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          KTor Exposed ORM engine powered by PostgreSQL with Flyway automated
          migrations.
        </p>
        <Link
          to={ROUTES.DISCORD.SERVERS}
          className={secondaryLinkClass}
          style={{ width: '100%', marginTop: 'auto' }}
        >
          View Connected Records
        </Link>
      </Card>
    </div>
  )
}
