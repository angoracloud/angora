import { useState } from 'react'
import { APP_ROUTES, DISCORD_CONFIG } from '../../constants'
import type { DiscordServer, InviteData, DiscordManagerTab } from '../../types'
import { ConnectedServersTab } from './tabs/ConnectedServersTab'
import { SlashCommandsTab } from './tabs/SlashCommandsTab'
import { BackendHealthTab } from './tabs/BackendHealthTab'

interface DiscordPageProps {
  servers: DiscordServer[]
  inviteData: InviteData | null
  loading: boolean
  error: string | null
  onNavigate: (path: string) => void
  onLeaveServer: (id: string, name: string) => void
  onDeleteServer: (id: string, name: string) => void
}

export function DiscordPage({
  servers,
  inviteData,
  loading,
  error,
  onNavigate,
  onLeaveServer,
  onDeleteServer,
}: DiscordPageProps) {
  const [activeTab, setActiveTab] = useState<DiscordManagerTab>('discord')
  const inviteUrl = inviteData?.inviteUrl || DISCORD_CONFIG.FALLBACK_INVITE_URL

  return (
    <main>
      {/* Breadcrumbs Navigation Bar */}
      <div className="breadcrumb-bar">
        <span
          className="breadcrumb-item"
          onClick={() => onNavigate(APP_ROUTES.HOME)}
        >
          🏠 Overview
        </span>
        <span>/</span>
        <span className="breadcrumb-active">
          🎮 Discord Bot Manager (/discordbot)
        </span>
      </div>

      {/* Action Bar */}
      <div className="action-bar">
        <div>
          <h1 className="page-title">Discord Server Integration</h1>
          <p className="page-subtitle">
            Manage connected Discord servers, invite Angora Bot, and view slash
            commands.
          </p>
        </div>
        <div className="btn-group">
          <button
            className="btn btn-secondary"
            onClick={() => onNavigate(APP_ROUTES.HOME)}
          >
            ← Back to Home
          </button>
          <a
            href={inviteUrl}
            target="_blank"
            rel="noreferrer"
            className="btn btn-discord"
          >
            🤖 Add Bot to Server (OAuth)
          </a>
        </div>
      </div>

      {/* Discord Navigation Sub-tabs */}
      <nav className="nav-tabs">
        <button
          className={`tab-btn ${activeTab === 'discord' ? 'active' : ''}`}
          onClick={() => setActiveTab('discord')}
        >
          🎮 Connected Servers ({servers.length})
        </button>
        <button
          className={`tab-btn ${activeTab === 'commands' ? 'active' : ''}`}
          onClick={() => setActiveTab('commands')}
        >
          ⚡ Bot Slash Commands
        </button>
        <button
          className={`tab-btn ${activeTab === 'health' ? 'active' : ''}`}
          onClick={() => setActiveTab('health')}
        >
          💚 Backend Health
        </button>
      </nav>

      {/* Sub-tab views */}
      {activeTab === 'discord' && (
        <ConnectedServersTab
          servers={servers}
          inviteData={inviteData}
          loading={loading}
          error={error}
          onLeaveServer={onLeaveServer}
          onDeleteServer={onDeleteServer}
        />
      )}

      {activeTab === 'commands' && <SlashCommandsTab />}

      {activeTab === 'health' && <BackendHealthTab inviteData={inviteData} />}
    </main>
  )
}
