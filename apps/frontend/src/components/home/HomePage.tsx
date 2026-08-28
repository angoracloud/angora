import { Link } from '@tanstack/react-router'
import { Button, Card, ChannelIcon, Pill } from '../ui'
import btnStyles from '../ui/Button.module.css'
import { ROUTES } from '../../routes'
import { HOME_PAGE_STRINGS } from '../../strings'

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
          <Pill variant="positive">{HOME_PAGE_STRINGS.DISCORD.STATUS}</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          {HOME_PAGE_STRINGS.DISCORD.TITLE}
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          {HOME_PAGE_STRINGS.DISCORD.DESCRIPTION}
        </p>
        <Link
          to={ROUTES.DISCORD.SERVERS}
          className={primaryLinkClass}
          style={{ width: '100%', marginTop: 'auto' }}
        >
          {HOME_PAGE_STRINGS.DISCORD.CTA}
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
          <Pill variant="positive">{HOME_PAGE_STRINGS.SLACK.STATUS}</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          {HOME_PAGE_STRINGS.SLACK.TITLE}
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          {HOME_PAGE_STRINGS.SLACK.DESCRIPTION}
        </p>
        <Button
          variant="secondary"
          disabled
          style={{ width: '100%', marginTop: 'auto' }}
        >
          {HOME_PAGE_STRINGS.SLACK.CTA}
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
          <Pill variant="positive">{HOME_PAGE_STRINGS.EMAIL.STATUS}</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          {HOME_PAGE_STRINGS.EMAIL.TITLE}
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          {HOME_PAGE_STRINGS.EMAIL.DESCRIPTION}
        </p>
        <Button
          variant="secondary"
          disabled
          style={{ width: '100%', marginTop: 'auto' }}
        >
          {HOME_PAGE_STRINGS.EMAIL.CTA}
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
          <Pill variant="positive">{HOME_PAGE_STRINGS.POSTGRES.STATUS}</Pill>
        </div>
        <h3
          style={{ marginBottom: 'var(--space-3)', color: 'var(--color-navy)' }}
        >
          {HOME_PAGE_STRINGS.POSTGRES.TITLE}
        </h3>
        <p
          style={{
            color: 'var(--color-text-secondary)',
            fontSize: 'var(--font-size-sm)',
            marginBottom: 'var(--space-7)',
          }}
        >
          {HOME_PAGE_STRINGS.POSTGRES.DESCRIPTION}
        </p>
        <Link
          to={ROUTES.DISCORD.SERVERS}
          className={secondaryLinkClass}
          style={{ width: '100%', marginTop: 'auto' }}
        >
          {HOME_PAGE_STRINGS.POSTGRES.CTA}
        </Link>
      </Card>
    </div>
  )
}
