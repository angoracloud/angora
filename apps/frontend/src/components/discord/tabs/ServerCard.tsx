import { useState, useRef, useEffect } from 'react'
import { DISCORD_CONFIG } from '../../../constants'
import type { DiscordServer } from '../../../types'

interface ServerCardProps {
  server: DiscordServer
  inviteUrl?: string
  onLeave: (id: string, name: string) => void
  onDelete: (id: string, name: string) => void
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
    <div className="card">
      <div className="server-header">
        <div className="server-icon">
          {server.iconUrl ? (
            <img src={server.iconUrl} alt={server.name} />
          ) : (
            server.name.substring(0, 2).toUpperCase()
          )}
        </div>
        <div className="server-info">
          <div className="server-name" title={server.name}>
            {server.name}
          </div>
          <div className="server-id">ID: {server.guildId}</div>
        </div>

        {/* 3-dots actions menu */}
        <div className="server-card-menu" ref={menuRef}>
          <button
            type="button"
            className="menu-trigger-btn"
            aria-label={`Options for ${server.name}`}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((prev) => !prev)}
          >
            <svg
              width="16"
              height="16"
              viewBox="0 0 16 16"
              fill="currentColor"
              aria-hidden="true"
            >
              <circle cx="8" cy="3" r="1.5" />
              <circle cx="8" cy="8" r="1.5" />
              <circle cx="8" cy="13" r="1.5" />
            </svg>
          </button>

          {menuOpen && (
            <div className="server-dropdown-menu" role="menu">
              <button
                type="button"
                className="server-dropdown-item danger"
                role="menuitem"
                onClick={() => {
                  setMenuOpen(false)
                  onDelete(server.id, server.name)
                }}
              >
                <span className="dropdown-item-icon">🗑️</span>
                <span>Delete Server</span>
              </button>
            </div>
          )}
        </div>
      </div>

      <div
        style={{
          fontSize: '0.85rem',
          color: 'var(--text-secondary)',
          marginBottom: '0.5rem',
        }}
      >
        👥 Members: <strong>{server.memberCount}</strong>
      </div>

      <div className="server-meta">
        <span className={`status-badge ${isConnected ? 'active' : 'inactive'}`}>
          <span className="status-dot"></span>
          {isConnected ? 'Bot Connected' : 'Bot Left'}
        </span>

        {isConnected ? (
          <button
            className="btn btn-danger"
            onClick={() => onLeave(server.id, server.name)}
          >
            Remove
          </button>
        ) : (
          <a
            href={fallbackUrl}
            target="_blank"
            rel="noreferrer"
            className="btn btn-primary"
          >
            🔄 Reconnect
          </a>
        )}
      </div>
    </div>
  )
}
