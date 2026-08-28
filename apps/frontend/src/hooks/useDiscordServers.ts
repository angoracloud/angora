import { useState, useEffect, useCallback } from 'react'
import { TIMING_CONFIG, TOAST_MESSAGES } from '../constants'
import { discordService } from '../services/discordService'
import type { DiscordServer, InviteData } from '../types'
import { useToast } from './useToast'

export function useDiscordServers() {
  const [servers, setServers] = useState<DiscordServer[]>([])
  const [inviteData, setInviteData] = useState<InviteData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const { addToast } = useToast()

  // Background silent fetch
  const fetchServersSilently = useCallback(async () => {
    try {
      const data = await discordService.getAllServers()
      setServers(data)
      setError(null)
      setLoading(false)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Connection error'
      setError(message)
      setLoading(false)
    }
  }, [])

  // Periodic polling & initial load
  useEffect(() => {
    let isMounted = true

    const loadInitialData = async () => {
      try {
        const [serverData, inviteInfo] = await Promise.all([
          discordService.getAllServers(),
          discordService.getInviteInfo().catch(() => null),
        ])
        if (isMounted) {
          setServers(serverData)
          if (inviteInfo) setInviteData(inviteInfo)
          setError(null)
          setLoading(false)
        }
      } catch (err: unknown) {
        if (isMounted) {
          const message =
            err instanceof Error ? err.message : 'Failed to load initial data'
          setError(message)
          setLoading(false)
        }
      }
    }

    void loadInitialData()

    const pollInterval = setInterval(() => {
      if (isMounted) {
        void fetchServersSilently()
      }
    }, TIMING_CONFIG.BACKGROUND_POLL_INTERVAL_MS)

    const handleFocus = () => {
      if (isMounted) {
        void fetchServersSilently()
      }
    }
    window.addEventListener('focus', handleFocus)

    return () => {
      isMounted = false
      clearInterval(pollInterval)
      window.removeEventListener('focus', handleFocus)
    }
  }, [fetchServersSilently])

  // Disconnect server action
  const leaveServer = useCallback(
    async (id: string, serverName?: string) => {
      if (
        !confirm(
          `Are you sure you want to disconnect ${serverName || 'this Discord server'}?`,
        )
      ) {
        return
      }

      try {
        await discordService.leaveServer(id)
        // Optimistically update UI
        setServers((prev) =>
          prev.map((s) =>
            s.id === id || s.guildId === id ? { ...s, botJoined: false } : s,
          ),
        )

        // Dispatch success toast
        const toastData = TOAST_MESSAGES.BOT_LEFT_SERVER(serverName)
        addToast('info', toastData.title, toastData.message)
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Network error'
        const toastData = TOAST_MESSAGES.SERVER_DISCONNECT_FAILED(
          serverName,
          message,
        )
        addToast('error', toastData.title, toastData.message)
      }
    },
    [addToast],
  )

  // Soft-delete server action
  const deleteServer = useCallback(
    async (id: string, serverName?: string) => {
      if (
        !confirm(
          `Are you sure you want to delete "${serverName || 'this Discord server'}"? This will remove it from the dashboard.`,
        )
      ) {
        return
      }

      try {
        await discordService.deleteServer(id)
        // Optimistically remove from UI
        setServers((prev) =>
          prev.filter((s) => s.id !== id && s.guildId !== id),
        )

        // Dispatch success toast
        const toastData = TOAST_MESSAGES.SERVER_DELETED(serverName)
        addToast('info', toastData.title, toastData.message)
      } catch (err: unknown) {
        const message = err instanceof Error ? err.message : 'Network error'
        const toastData = TOAST_MESSAGES.SERVER_DELETE_FAILED(
          serverName,
          message,
        )
        addToast('error', toastData.title, toastData.message)
      }
    },
    [addToast],
  )

  return {
    servers,
    inviteData,
    loading,
    error,
    leaveServer,
    deleteServer,
  }
}
