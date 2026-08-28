import { useState, useRef, useEffect } from 'react'
import { MoreVertical, Trash2 } from 'lucide-react'
import { DISCORD_CONFIG } from '../../../constants'
import { SERVER_CARD_STRINGS } from '../../../strings'
import type { DiscordServer } from '../../../types'
import {
  Avatar,
  Button,
  Card,
  IconButton,
  LinkButton,
  StatusDot,
} from '../../ui'
import styles from './ServerCard.module.css'

interface ServerCardProps {
  server: DiscordServer
  inviteUrl?: string
  onLeave: (id: string, name?: string) => void
  onDelete: (id: string, name?: string) => void
}

export function ServerCard({
  server,
  inviteUrl,
  onLeave,
  onDelete,
}: ServerCardProps) {
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const isConnected = server.botJoined !== false
  const fallbackUrl = inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  // Close dropdown on outside click or Escape key
  useEffect(() => {
    if (!menuOpen) return

    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setMenuOpen(false)
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMenuOpen(false)
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  return (
    <Card>
      <div className={styles.header}>
        <Avatar name={server.name} imageUrl={server.iconUrl} size="lg" />
        <div className={styles.headerInfo}>
          <div className={styles.serverName} title={server.name}>
            {server.name}
          </div>
          <div className={styles.serverId}>
            {SERVER_CARD_STRINGS.ID_LABEL} {server.guildId}
          </div>
        </div>

        {/* 3-dots actions menu */}
        <div className={styles.menuContainer} ref={menuRef}>
          <IconButton
            icon={MoreVertical}
            label={SERVER_CARD_STRINGS.OPTIONS_LABEL(server.name)}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((prev) => !prev)}
          />

          {menuOpen && (
            <div className={styles.dropdown} role="menu">
              <button
                type="button"
                className={styles.dropdownItemDanger}
                role="menuitem"
                onClick={() => {
                  setMenuOpen(false)
                  onDelete(server.id, server.name)
                }}
              >
                <Trash2 size={14} />
                <span>{SERVER_CARD_STRINGS.DELETE_SERVER}</span>
              </button>
            </div>
          )}
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
